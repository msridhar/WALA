function source() {
    return "secret:000111"
}

function source2() {
    return new String("secret:000111")
}

function main() {
    var info = source()
    var res = info.replace("secret:", "")
}

function main2() {
    var info = source2()
    var res = info.replace("secret:", "")

}

main()
main2()