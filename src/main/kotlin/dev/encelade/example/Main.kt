package dev.encelade.example

import dev.encelade.example.dao.codegen.Tables.PERSON
import dev.encelade.example.dao.codegen.tables.daos.PersonDao
import dev.encelade.example.dao.codegen.tables.pojos.Person

fun main() {
    val dslContext = DaoService.getDslContext("example.db")

    dslContext.transaction { cfg ->
        val personDao = PersonDao(cfg)
        val person = Person()
        person.firstName = "Charles"
        person.lastName = "Baudelaire"
        personDao.insert(person)
    }

    val count = dslContext
            .selectCount()
            .from(PERSON)
            .fetchOneInto(Int::class.java)

    println("entries: $count")
}
