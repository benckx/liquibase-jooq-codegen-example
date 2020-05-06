<a href="https://paypal.me/benckx/2">
<img src="https://img.shields.io/badge/Donate-PayPal-green.svg"/>
</a>

# About

During build, the Liquibase schema is executed to a H2 in-memory DB, 
jOOQ DAO code is generated based on this schema. 

## How To
To run it locally:
* `./gradlew clean build` to generate the jOOQ DAO code
* Run the main class

## Technical stack

* Kotlin 
* SQLite
* Liquibase
* jOOQ (with code generation)
