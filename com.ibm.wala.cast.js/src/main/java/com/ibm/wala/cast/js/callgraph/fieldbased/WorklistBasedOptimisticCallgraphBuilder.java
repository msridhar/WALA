/*
 * Copyright (c) 2013 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 */
package com.ibm.wala.cast.js.callgraph.fieldbased;

import com.ibm.wala.cast.js.callgraph.fieldbased.flowgraph.FlowGraph;
import com.ibm.wala.cast.js.callgraph.fieldbased.flowgraph.FlowGraphBuilder;
import com.ibm.wala.cast.js.callgraph.fieldbased.flowgraph.vertices.CallVertex;
import com.ibm.wala.cast.js.callgraph.fieldbased.flowgraph.vertices.FuncVertex;
import com.ibm.wala.cast.js.callgraph.fieldbased.flowgraph.vertices.VarVertex;
import com.ibm.wala.cast.js.callgraph.fieldbased.flowgraph.vertices.Vertex;
import com.ibm.wala.cast.js.callgraph.fieldbased.flowgraph.vertices.VertexFactory;
import com.ibm.wala.cast.js.ipa.callgraph.JSAnalysisOptions;
import com.ibm.wala.cast.js.ssa.JavaScriptInvoke;
import com.ibm.wala.cast.js.types.JavaScriptMethods;
import com.ibm.wala.cast.types.AstMethodReference;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.MonitorUtil;
import com.ibm.wala.util.MonitorUtil.IProgressMonitor;
import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.Iterator2Iterable;
import com.ibm.wala.util.collections.MapUtil;
import com.ibm.wala.util.collections.Pair;
import java.util.Map;
import java.util.Set;

import com.ibm.wala.cast.js.ipa.callgraph.JSCallGraph;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.cast.js.ipa.summaries.JavaScriptConstructorFunctions;
import com.ibm.wala.cast.js.ipa.summaries.JavaScriptConstructorFunctions.JavaScriptConstructor;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.cast.js.ipa.callgraph.JavaScriptFunctionDotCallTargetSelector;
import com.ibm.wala.cast.js.ipa.callgraph.JavaScriptFunctionApplyTargetSelector;
import java.util.Iterator;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.File; 
import java.io.FileWriter;
import com.ibm.wala.cast.js.util.CallGraph2JSON;

/**
 * Optimistic call graph builder that propagates inter-procedural data flow iteratively as call
 * edges are discovered. Slower, but potentially more sound than {@link
 * PessimisticCallGraphBuilder}.
 *
 * <p>This variant uses a worklist algorithm, generally making it scale better than {@link
 * OptimisticCallgraphBuilder}, which repeatedly runs the pessimistic algorithm.
 *
 * @author mschaefer
 */
public class WorklistBasedOptimisticCallgraphBuilder extends FieldBasedCallGraphBuilder {
  /** The maximum number of iterations to perform. */
  public int ITERATION_CUTOFF = Integer.MAX_VALUE;

  private final boolean handleCallApply;

  private FlowGraphBuilder builder;

  public WorklistBasedOptimisticCallgraphBuilder(
      IClassHierarchy cha,
      AnalysisOptions options,
      IAnalysisCacheView cache,
      boolean supportFullPointerAnalysis) {
    super(cha, options, cache, supportFullPointerAnalysis);
    handleCallApply =
        options instanceof JSAnalysisOptions && ((JSAnalysisOptions) options).handleCallApply();
  }

  @Override
  public FlowGraph buildFlowGraph(IProgressMonitor monitor) throws CancelException {
    builder = new FlowGraphBuilder(cha, cache, false);
    return builder.buildFlowGraph();
  }
  
  public Set<Pair<CallVertex, FuncVertex>> extractCallGraphEdgesWL(
      FlowGraph flowgraph, IProgressMonitor monitor, JSCallGraph cg, String destAddress) throws CancelException {
    VertexFactory factory = flowgraph.getVertexFactory();
    Set<Vertex> worklist = HashSetFactory.make();
    Map<Vertex, Set<FuncVertex>> reachingFunctions = HashMapFactory.make();
    Map<VarVertex, Pair<JavaScriptInvoke, Boolean>> reflectiveCalleeVertices =
        HashMapFactory.make();



    for (Vertex v : flowgraph) {
      if (v instanceof FuncVertex) {
        FuncVertex fv = (FuncVertex) v;
        worklist.add(fv);
        MapUtil.findOrCreateSet(reachingFunctions, fv).add(fv);
      }
    }
    int cnt =0;
    System.out.println("Entering Iterative Loop");
    //System.out.println(DestAddress.destAddress);
    System.out.println(worklist.size());
    while (!worklist.isEmpty()) {
      MonitorUtil.throwExceptionIfCanceled(monitor);

      Vertex v = worklist.iterator().next();
      worklist.remove(v);
      Set<FuncVertex> vReach = MapUtil.findOrCreateSet(reachingFunctions, v);
      for (Vertex w : Iterator2Iterable.make(flowgraph.getSucc(v))) {
        MonitorUtil.throwExceptionIfCanceled(monitor);

        Set<FuncVertex> wReach = MapUtil.findOrCreateSet(reachingFunctions, w);
        boolean changed = false;
        if (w instanceof CallVertex) {
          for (FuncVertex fv : vReach) {
            if (wReach.add(fv)) {
              changed = true;
              CallVertex callVertex = (CallVertex) w;
              addCallEdge(flowgraph, callVertex, fv, worklist);

              // special handling of invocations of Function.prototype.call
              String fullName = fv.getFullName();
              if (handleCallApply
                  && changed
                  && (fullName.equals("Lprologue.js/Function_prototype_call")
                      || fullName.equals("Lprologue.js/Function_prototype_apply"))) {
                JavaScriptInvoke invk = callVertex.getInstruction();
                VarVertex reflectiveCalleeVertex =
                    factory.makeVarVertex(callVertex.getCaller(), invk.getUse(1));
                flowgraph.addEdge(
                    reflectiveCalleeVertex,
                    factory.makeReflectiveCallVertex(callVertex.getCaller(), invk));
                // we only add dataflow edges for Function.prototype.call
                boolean isCall = fullName.equals("Lprologue.js/Function_prototype_call");
                reflectiveCalleeVertices.put(reflectiveCalleeVertex, Pair.make(invk, isCall));
                for (FuncVertex fw :
                    MapUtil.findOrCreateSet(reachingFunctions, reflectiveCalleeVertex))
                  addReflectiveCallEdge(
                      flowgraph, reflectiveCalleeVertex, invk, fw, worklist, isCall);
              }
            }
          }
        } else if (handleCallApply && reflectiveCalleeVertices.containsKey(w)) {
          Pair<JavaScriptInvoke, Boolean> invkAndIsCall = reflectiveCalleeVertices.get(w);
          for (FuncVertex fv : vReach) {
            if (wReach.add(fv)) {
              changed = true;
              addReflectiveCallEdge(
                  flowgraph, (VarVertex) w, invkAndIsCall.fst, fv, worklist, invkAndIsCall.snd);
            }
          }
        } else {

          changed = wReach.addAll(vReach);
        }
        if (changed) worklist.add(w);
      }
      cnt++;
      /*if(cnt%3007 == 0){
      //Madhurima_ACG
      Set<Pair<CallVertex, FuncVertex>> res = HashSetFactory.make();
      for (Map.Entry<Vertex, Set<FuncVertex>> entry : reachingFunctions.entrySet()) {
        final Vertex vIter = entry.getKey();
        if (vIter instanceof CallVertex)
          for (FuncVertex fv : entry.getValue()) res.add(Pair.make((CallVertex) vIter, fv));
      }
      //JSCallGraph cg = extractInterCG(res, flowgraph, monitor);
      for (Pair<CallVertex, FuncVertex> edge : res) { 
      CallVertex callVertex = edge.fst;
      FuncVertex targetVertex = edge.snd;
      IClass kaller = callVertex.getCaller().getConcreteType();
      CGNode caller =
          cg.findOrCreateNode(
              kaller.getMethod(AstMethodReference.fnSelector), Everywhere.EVERYWHERE);
      CallSiteReference site = callVertex.getSite();
      IMethod target = targetSelector.getCalleeTarget(caller, site, targetVertex.getConcreteType());
      boolean isFunctionPrototypeCall =
          target != null
              && target
                  .getName()
                  .toString()
                  .startsWith(JavaScriptFunctionDotCallTargetSelector.SYNTHETIC_CALL_METHOD_PREFIX);
      boolean isFunctionPrototypeApply =
          target != null
              && target
                  .getName()
                  .toString()
                  .startsWith(JavaScriptFunctionApplyTargetSelector.SYNTHETIC_APPLY_METHOD_PREFIX);

      if (isFunctionPrototypeCall || isFunctionPrototypeApply) {
        handleFunctionCallOrApplyInvocation(
            flowgraph, monitor, cg, callVertex, caller, site, target);
      } else {
        addEdgeToJSCallGraph(cg, site, target, caller);

        if (target instanceof JavaScriptConstructor) {
          IMethod fun =
              ((JavaScriptConstructor) target)
                  .constructedType()
                  .getMethod(AstMethodReference.fnSelector);
          CGNode ctorCaller = cg.findOrCreateNode(target, Everywhere.EVERYWHERE);

          CallSiteReference ref = null;
          Iterator<CallSiteReference> sites = ctorCaller.iterateCallSites();
          while (sites.hasNext()) {
            CallSiteReference r = sites.next();
            if (r.getDeclaredTarget().getSelector().equals(AstMethodReference.fnSelector)) {
              ref = r;
              break;
            }
          }

          if (ref != null) {
            addEdgeToJSCallGraph(cg, ref, fun, ctorCaller);
          }
        }
      }
    }
    File file1 = new File(destAddress+"SCG"+cnt+".json");
    try {  
      FileWriter myWriter1 = new FileWriter(file1);
      myWriter1.write((new CallGraph2JSON(false,true)).serialize(cg));
      myWriter1.close();
      System.out.println("Successfully wrote to Call Graph "+ file1);
    } catch (IOException e) {
      System.out.println("An error occurred while writing Call Graph.");
      e.printStackTrace();
    }
    //Madhurima_ACG
    }*/
    }
    System.out.println("Total Iterations : "+ cnt);
    Set<Pair<CallVertex, FuncVertex>> res = HashSetFactory.make();
    for (Map.Entry<Vertex, Set<FuncVertex>> entry : reachingFunctions.entrySet()) {
      final Vertex v = entry.getKey();
      if (v instanceof CallVertex)
        for (FuncVertex fv : entry.getValue()) res.add(Pair.make((CallVertex) v, fv));
    }
    return res;
  }

  // add flow corresponding to a new call edge
  private void addCallEdge(
      FlowGraph flowgraph, CallVertex c, FuncVertex callee, Set<Vertex> worklist) {
    VertexFactory factory = flowgraph.getVertexFactory();
    FuncVertex caller = c.getCaller();
    JavaScriptInvoke invk = c.getInstruction();

    int offset = 0;
    if (invk.getDeclaredTarget()
        .getSelector()
        .equals(JavaScriptMethods.ctorReference.getSelector())) {
      offset = 1;
    }

    for (int i = 0; i < invk.getNumberOfPositionalParameters(); ++i) {
      // only flow receiver into 'this' if invk is, in fact, a method call
      flowgraph.addEdge(
          factory.makeVarVertex(caller, invk.getUse(i)), factory.makeArgVertex(callee));
      if (i != 1 || !invk.getDeclaredTarget().getSelector().equals(AstMethodReference.fnSelector))
        addFlowEdge(
            flowgraph,
            factory.makeVarVertex(caller, invk.getUse(i)),
            factory.makeParamVertex(callee, i + offset),
            worklist);
    }

    // flow from return vertex to result vertex
    addFlowEdge(
        flowgraph,
        factory.makeRetVertex(callee),
        factory.makeVarVertex(caller, invk.getDef()),
        worklist);
  }

  public void addFlowEdge(FlowGraph flowgraph, Vertex from, Vertex to, Set<Vertex> worklist) {
    flowgraph.addEdge(from, to);
    worklist.add(from);
  }

  // add data flow corresponding to a reflective invocation via Function.prototype.call
  // NB: for f.call(...), f will _not_ appear as a call target, but the appropriate argument and
  // return data flow will be set up
  private void addReflectiveCallEdge(
      FlowGraph flowgraph,
      VarVertex reflectiveCallee,
      JavaScriptInvoke invk,
      FuncVertex realCallee,
      Set<Vertex> worklist,
      boolean isFunctionPrototypeCall) {
    VertexFactory factory = flowgraph.getVertexFactory();
    FuncVertex caller = reflectiveCallee.getFunction();

    if (isFunctionPrototypeCall) {
      // flow from arguments to parameters
      for (int i = 2; i < invk.getNumberOfPositionalParameters(); ++i) {
        addFlowEdge(
            flowgraph,
            factory.makeVarVertex(caller, invk.getUse(i)),
            factory.makeParamVertex(realCallee, i - 1),
            worklist);
      }
    }

    // flow from return vertex to result vertex
    addFlowEdge(
        flowgraph,
        factory.makeRetVertex(realCallee),
        factory.makeVarVertex(caller, invk.getDef()),
        worklist);
  }
}
