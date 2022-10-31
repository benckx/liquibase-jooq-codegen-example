package dev.encelade.example

import dev.encelade.example.dao.codegen.tables.daos.PersonDao
import dev.encelade.example.dao.codegen.tables.pojos.Person

fun main() {
    val dslContext = DaoService.getDslContext("example.db")
    val cfg = dslContext.configuration()
    val personDao = PersonDao(cfg)

    val person = Person()
    person.firstName = "Charles"
    person.lastName = "Baudelaire"
    personDao.insert(person)

    println("entries: ${personDao.count()}")
}
