package be.encelade.example

import be.encelade.example.dao.codegen.Tables.PERSON

fun main() {
    val dslContext = DaoService().dslContext

    dslContext
            .insertInto(PERSON)
            .set(PERSON.FIRST_NAME, "ben")
            .set(PERSON.LAST_NAME, "ckx")
            .execute()

    val count = dslContext
            .selectCount()
            .from(PERSON)
            .fetchOneInto(Int::class.java)

    println("entries: $count")
}
