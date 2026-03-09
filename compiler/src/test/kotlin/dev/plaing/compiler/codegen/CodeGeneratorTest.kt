package dev.plaing.compiler.codegen

import dev.plaing.compiler.parser.*
import java.io.File
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CodeGeneratorTest {

    // ---------------------------------------------------------------
    // Helper: parse a plaing source string and run EntityAnalyzer
    // ---------------------------------------------------------------

    private fun analyzeEntities(source: String): List<AnalyzedEntity> {
        val tokens = Lexer(source, "test.plaing").tokenize()
        val program = Parser(tokens, "test.plaing").parse()
        val entities = program.declarations.filterIsInstance<EntityDeclaration>()
        return EntityAnalyzer().analyze(entities)
    }

    private fun parseProgram(source: String): PlaingProgram {
        val tokens = Lexer(source, "test.plaing").tokenize()
        return Parser(tokens, "test.plaing").parse()
    }

    // ---------------------------------------------------------------
    // 1. TypeMapper tests
    // ---------------------------------------------------------------

    @Test
    fun `TypeMapper snakeToCamel converts simple names`() {
        assertEquals("firstName", TypeMapper.snakeToCamel("first_name"))
        assertEquals("id", TypeMapper.snakeToCamel("id"))
        assertEquals("createdAt", TypeMapper.snakeToCamel("created_at"))
        assertEquals("myLongFieldName", TypeMapper.snakeToCamel("my_long_field_name"))
    }

    @Test
    fun `TypeMapper toKotlinType maps plaing types correctly`() {
        assertEquals("String", TypeMapper.toKotlinType(PlaingType.TextType))
        assertEquals("Double", TypeMapper.toKotlinType(PlaingType.NumberType))
        assertEquals("Boolean", TypeMapper.toKotlinType(PlaingType.BooleanType))
        assertEquals("Long", TypeMapper.toKotlinType(PlaingType.DateType))
        assertEquals("Long", TypeMapper.toKotlinType(PlaingType.EntityRef("User")))
        assertEquals("List<String>", TypeMapper.toKotlinType(PlaingType.ListType(PlaingType.TextType)))
        assertEquals("String?", TypeMapper.toKotlinType(PlaingType.OptionalType(PlaingType.TextType)))
    }

    @Test
    fun `TypeMapper toKotlinType with nullable flag`() {
        assertEquals("String?", TypeMapper.toKotlinType(PlaingType.TextType, nullable = true))
        assertEquals("Double?", TypeMapper.toKotlinType(PlaingType.NumberType, nullable = true))
    }

    @Test
    fun `TypeMapper toSqlType maps correctly`() {
        assertEquals("TEXT", TypeMapper.toSqlType(PlaingType.TextType))
        assertEquals("REAL", TypeMapper.toSqlType(PlaingType.NumberType))
        assertEquals("INTEGER", TypeMapper.toSqlType(PlaingType.BooleanType))
        assertEquals("INTEGER", TypeMapper.toSqlType(PlaingType.DateType))
        assertEquals("INTEGER", TypeMapper.toSqlType(PlaingType.EntityRef("Post")))
        // Optional unwraps inner type
        assertEquals("TEXT", TypeMapper.toSqlType(PlaingType.OptionalType(PlaingType.TextType)))
    }

    @Test
    fun `TypeMapper toColumnName appends _id for entity refs`() {
        val loc = SourceLocation(1, 1, "test.plaing")
        val textField = FieldDefinition("name", PlaingType.TextType, emptyList(), loc)
        val refField = FieldDefinition("author", PlaingType.EntityRef("User"), emptyList(), loc)

        assertEquals("name", TypeMapper.toColumnName(textField))
        assertEquals("author_id", TypeMapper.toColumnName(refField))
    }

    @Test
    fun `TypeMapper toKotlinPropertyName converts entity ref fields`() {
        val loc = SourceLocation(1, 1, "test.plaing")
        val textField = FieldDefinition("first_name", PlaingType.TextType, emptyList(), loc)
        val refField = FieldDefinition("author", PlaingType.EntityRef("User"), emptyList(), loc)

        assertEquals("firstName", TypeMapper.toKotlinPropertyName(textField))
        assertEquals("authorId", TypeMapper.toKotlinPropertyName(refField))
    }

    @Test
    fun `TypeMapper isStoredInTable excludes list types`() {
        val loc = SourceLocation(1, 1, "test.plaing")
        val textField = FieldDefinition("name", PlaingType.TextType, emptyList(), loc)
        val listField = FieldDefinition("tags", PlaingType.ListType(PlaingType.TextType), emptyList(), loc)

        assertTrue(TypeMapper.isStoredInTable(textField))
        assertFalse(TypeMapper.isStoredInTable(listField))
    }

    @Test
    fun `TypeMapper modifier detection`() {
        val loc = SourceLocation(1, 1, "test.plaing")
        val requiredField = FieldDefinition("email", PlaingType.TextType, listOf(FieldModifier.Required), loc)
        val uniqueField = FieldDefinition("email", PlaingType.TextType, listOf(FieldModifier.Unique), loc)
        val hiddenField = FieldDefinition("password", PlaingType.TextType, listOf(FieldModifier.Hidden), loc)
        val plainField = FieldDefinition("bio", PlaingType.TextType, emptyList(), loc)

        assertTrue(TypeMapper.isRequired(requiredField))
        assertFalse(TypeMapper.isRequired(plainField))
        assertTrue(TypeMapper.isUnique(uniqueField))
        assertFalse(TypeMapper.isUnique(plainField))
        assertTrue(TypeMapper.isHidden(hiddenField))
        assertFalse(TypeMapper.isHidden(plainField))
    }

    @Test
    fun `TypeMapper isNullable detects optional type`() {
        val loc = SourceLocation(1, 1, "test.plaing")
        val optionalField = FieldDefinition("bio", PlaingType.OptionalType(PlaingType.TextType), emptyList(), loc)
        val requiredField = FieldDefinition("name", PlaingType.TextType, emptyList(), loc)

        assertTrue(TypeMapper.isNullable(optionalField))
        assertFalse(TypeMapper.isNullable(requiredField))
    }

    @Test
    fun `TypeMapper getDefault extracts default expression`() {
        val loc = SourceLocation(1, 1, "test.plaing")
        val defaultExpr = StringLiteral("member", loc)
        val fieldWithDefault = FieldDefinition("role", PlaingType.TextType, listOf(FieldModifier.Default(defaultExpr)), loc)
        val fieldWithoutDefault = FieldDefinition("name", PlaingType.TextType, emptyList(), loc)

        val result = TypeMapper.getDefault(fieldWithDefault)
        assertNotNull(result)
        assertTrue(result is StringLiteral)
        assertEquals("member", (result as StringLiteral).value)
        assertEquals(null, TypeMapper.getDefault(fieldWithoutDefault))
    }

    @Test
    fun `TypeMapper defaultValueToKotlin formats values correctly`() {
        val loc = SourceLocation(1, 1, "test.plaing")
        assertEquals("\"hello\"", TypeMapper.defaultValueToKotlin(StringLiteral("hello", loc)))
        assertEquals("42", TypeMapper.defaultValueToKotlin(NumberLiteral(42.0, loc)))
        assertEquals("3.14", TypeMapper.defaultValueToKotlin(NumberLiteral(3.14, loc)))
        assertEquals("true", TypeMapper.defaultValueToKotlin(BooleanLiteral(true, loc)))
        assertEquals("false", TypeMapper.defaultValueToKotlin(BooleanLiteral(false, loc)))
        assertEquals("System.currentTimeMillis()", TypeMapper.defaultValueToKotlin(NowLiteral(loc)))
    }

    @Test
    fun `TypeMapper defaultValueToSql formats values correctly`() {
        val loc = SourceLocation(1, 1, "test.plaing")
        assertEquals("'hello'", TypeMapper.defaultValueToSql(StringLiteral("hello", loc)))
        assertEquals("42", TypeMapper.defaultValueToSql(NumberLiteral(42.0, loc)))
        assertEquals("1", TypeMapper.defaultValueToSql(BooleanLiteral(true, loc)))
        assertEquals("0", TypeMapper.defaultValueToSql(BooleanLiteral(false, loc)))
        assertEquals("(strftime('%s','now') * 1000)", TypeMapper.defaultValueToSql(NowLiteral(loc)))
    }

    // ---------------------------------------------------------------
    // 2. EntityAnalyzer tests
    // ---------------------------------------------------------------

    @Test
    fun `EntityAnalyzer filters out list fields from storedFields`() {
        val entities = analyzeEntities("""
entity Post:
  title is Text, required
  tags is List of Text
  body is Text
""".trimIndent())

        assertEquals(1, entities.size)
        val post = entities[0]
        // tags (List) should NOT be in storedFields
        assertEquals(2, post.storedFields.size)
        assertEquals("title", post.storedFields[0].name)
        assertEquals("body", post.storedFields[1].name)
    }

    @Test
    fun `EntityAnalyzer detects foreign keys`() {
        val entities = analyzeEntities("""
entity User:
  name is Text, required

entity Post:
  title is Text, required
  author is User
""".trimIndent())

        // User should come first (topologically sorted)
        assertEquals("User", entities[0].declaration.name)
        assertEquals("Post", entities[1].declaration.name)

        val postEntity = entities[1]
        assertTrue(postEntity.foreignKeys.containsKey("author_id"))
        assertEquals("User", postEntity.foreignKeys["author_id"])
    }

    @Test
    fun `EntityAnalyzer detects required and unique fields`() {
        val entities = analyzeEntities("""
entity User:
  name is Text, required
  email is Text, required, unique
  bio is Text
""".trimIndent())

        val user = entities[0]
        assertTrue("name" in user.requiredFields)
        assertTrue("email" in user.requiredFields)
        assertFalse("bio" in user.requiredFields)
        assertTrue("email" in user.uniqueFields)
        assertFalse("name" in user.uniqueFields)
    }

    @Test
    fun `EntityAnalyzer detects hidden fields`() {
        val entities = analyzeEntities("""
entity User:
  name is Text, required
  password is Text, required, hidden
""".trimIndent())

        val user = entities[0]
        assertTrue("password" in user.hiddenFields)
        assertFalse("name" in user.hiddenFields)
    }

    @Test
    fun `EntityAnalyzer extracts default values`() {
        val entities = analyzeEntities("""
entity User:
  name is Text, required
  role is Text, default "member"
  active is Boolean, default true
  created_at is Date, default now
""".trimIndent())

        val user = entities[0]
        assertEquals(3, user.defaults.size)
        assertTrue(user.defaults["role"] is StringLiteral)
        assertTrue(user.defaults["active"] is BooleanLiteral)
        assertTrue(user.defaults["created_at"] is NowLiteral)
    }

    @Test
    fun `EntityAnalyzer topological sort puts referenced entities first`() {
        // Define Post before User in source but Post references User
        val entities = analyzeEntities("""
entity Post:
  title is Text, required
  author is User

entity User:
  name is Text, required
""".trimIndent())

        // User should come first because Post references it
        assertEquals("User", entities[0].declaration.name)
        assertEquals("Post", entities[1].declaration.name)
    }

    @Test
    fun `EntityAnalyzer topological sort handles chain of dependencies`() {
        val entities = analyzeEntities("""
entity Comment:
  text is Text
  post is Post

entity Post:
  title is Text
  author is User

entity User:
  name is Text
""".trimIndent())

        // User -> Post -> Comment
        assertEquals("User", entities[0].declaration.name)
        assertEquals("Post", entities[1].declaration.name)
        assertEquals("Comment", entities[2].declaration.name)
    }

    // ---------------------------------------------------------------
    // 3. KotlinDataClassGenerator tests
    // ---------------------------------------------------------------

    @Test
    fun `KotlinDataClassGenerator produces correct class declaration`() {
        val entities = analyzeEntities("""
entity User:
  name is Text, required
  email is Text, required, unique
""".trimIndent())

        val gen = KotlinDataClassGenerator()
        val code = gen.generate(entities[0], "com.example.model")

        assertTrue(code.contains("package com.example.model"))
        assertTrue(code.contains("data class User("))
        assertTrue(code.contains("val id: Long = 0,"))
        assertTrue(code.contains("val name: String,"))
        assertTrue(code.contains("val email: String,"))
    }

    @Test
    fun `KotlinDataClassGenerator handles default values`() {
        val entities = analyzeEntities("""
entity User:
  name is Text, required
  role is Text, default "member"
  active is Boolean, default true
""".trimIndent())

        val gen = KotlinDataClassGenerator()
        val code = gen.generate(entities[0], "com.example.model")

        // Fields without defaults come first (after id)
        assertTrue(code.contains("val name: String,"))
        // Fields with defaults come after
        assertTrue(code.contains("val role: String = \"member\","))
        assertTrue(code.contains("val active: Boolean = true,"))
    }

    @Test
    fun `KotlinDataClassGenerator handles optional fields`() {
        val entities = analyzeEntities("""
entity User:
  name is Text, required
  bio is Optional Text
""".trimIndent())

        val gen = KotlinDataClassGenerator()
        val code = gen.generate(entities[0], "com.example.model")

        assertTrue(code.contains("val name: String,"))
        assertTrue(code.contains("val bio: String? = null,"))
    }

    @Test
    fun `KotlinDataClassGenerator handles entity references`() {
        val entities = analyzeEntities("""
entity User:
  name is Text

entity Post:
  title is Text
  author is User
""".trimIndent())

        val gen = KotlinDataClassGenerator()
        val postEntity = entities.first { it.declaration.name == "Post" }
        val code = gen.generate(postEntity, "com.example.model")

        assertTrue(code.contains("data class Post("))
        assertTrue(code.contains("val authorId: Long,"))
    }

    @Test
    fun `KotlinDataClassGenerator handles snake_case field names`() {
        val entities = analyzeEntities("""
entity User:
  first_name is Text
  created_at is Date, default now
""".trimIndent())

        val gen = KotlinDataClassGenerator()
        val code = gen.generate(entities[0], "com.example.model")

        assertTrue(code.contains("val firstName: String,"))
        assertTrue(code.contains("val createdAt: Long"))
    }

    // ---------------------------------------------------------------
    // 4. SqlSchemaGenerator tests
    // ---------------------------------------------------------------

    @Test
    fun `SqlSchemaGenerator produces CREATE TABLE statement`() {
        val entities = analyzeEntities("""
entity User:
  name is Text, required
  email is Text, required, unique
""".trimIndent())

        val gen = SqlSchemaGenerator()
        val sql = gen.generateCreateTable(entities[0])

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS User"))
        assertTrue(sql.contains("id INTEGER PRIMARY KEY AUTOINCREMENT"))
        assertTrue(sql.contains("name TEXT NOT NULL"))
        assertTrue(sql.contains("email TEXT NOT NULL UNIQUE"))
    }

    @Test
    fun `SqlSchemaGenerator handles default values`() {
        val entities = analyzeEntities("""
entity User:
  role is Text, default "member"
  active is Boolean, default true
""".trimIndent())

        val gen = SqlSchemaGenerator()
        val sql = gen.generateCreateTable(entities[0])

        assertTrue(sql.contains("role TEXT NOT NULL DEFAULT 'member'"))
        assertTrue(sql.contains("active INTEGER NOT NULL DEFAULT 1"))
    }

    @Test
    fun `SqlSchemaGenerator handles foreign key references`() {
        val entities = analyzeEntities("""
entity User:
  name is Text

entity Post:
  title is Text
  author is User
""".trimIndent())

        val gen = SqlSchemaGenerator()
        val postEntity = entities.first { it.declaration.name == "Post" }
        val sql = gen.generateCreateTable(postEntity)

        assertTrue(sql.contains("author_id INTEGER NOT NULL REFERENCES User(id)"))
    }

    @Test
    fun `SqlSchemaGenerator handles optional fields`() {
        val entities = analyzeEntities("""
entity User:
  name is Text, required
  bio is Optional Text
""".trimIndent())

        val gen = SqlSchemaGenerator()
        val sql = gen.generateCreateTable(entities[0])

        // Optional fields should NOT have NOT NULL
        assertTrue(sql.contains("name TEXT NOT NULL"))
        assertTrue(sql.contains("bio TEXT"))
        // Ensure bio does not have NOT NULL
        val bioLine = sql.lines().first { it.trimStart().startsWith("bio") }
        assertFalse(bioLine.contains("NOT NULL"))
    }

    @Test
    fun `SqlSchemaGenerator handles date default now`() {
        val entities = analyzeEntities("""
entity Event:
  created_at is Date, default now
""".trimIndent())

        val gen = SqlSchemaGenerator()
        val sql = gen.generateCreateTable(entities[0])

        assertTrue(sql.contains("created_at INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)"))
    }

    @Test
    fun `SqlSchemaGenerator generateAllSchemas combines schemas`() {
        val entities = analyzeEntities("""
entity User:
  name is Text

entity Post:
  title is Text
  author is User
""".trimIndent())

        val gen = SqlSchemaGenerator()
        val allSql = gen.generateAllSchemas(entities)

        assertTrue(allSql.contains("CREATE TABLE IF NOT EXISTS User"))
        assertTrue(allSql.contains("CREATE TABLE IF NOT EXISTS Post"))
    }

    @Test
    fun `SqlSchemaGenerator maps number type to REAL`() {
        val entities = analyzeEntities("""
entity Product:
  price is Number
""".trimIndent())

        val gen = SqlSchemaGenerator()
        val sql = gen.generateCreateTable(entities[0])

        assertTrue(sql.contains("price REAL NOT NULL"))
    }

    // ---------------------------------------------------------------
    // 5. RepositoryGenerator tests
    // ---------------------------------------------------------------

    @Test
    fun `RepositoryGenerator produces correct class structure`() {
        val entities = analyzeEntities("""
entity User:
  name is Text, required
  email is Text, required, unique
""".trimIndent())

        val gen = RepositoryGenerator()
        val code = gen.generate(entities[0], "com.example.model", "com.example.db")

        assertTrue(code.contains("package com.example.db"))
        assertTrue(code.contains("import com.example.model.User"))
        assertTrue(code.contains("class UserRepository(private val conn: Connection)"))
    }

    @Test
    fun `RepositoryGenerator contains findById method`() {
        val entities = analyzeEntities("""
entity User:
  name is Text
  email is Text
""".trimIndent())

        val gen = RepositoryGenerator()
        val code = gen.generate(entities[0], "com.example.model", "com.example.db")

        assertTrue(code.contains("fun findById(id: Long): User?"))
        assertTrue(code.contains("SELECT id, name, email FROM User WHERE id = ?"))
    }

    @Test
    fun `RepositoryGenerator contains findWhere method`() {
        val entities = analyzeEntities("""
entity User:
  name is Text
""".trimIndent())

        val gen = RepositoryGenerator()
        val code = gen.generate(entities[0], "com.example.model", "com.example.db")

        assertTrue(code.contains("fun findWhere(vararg conditions: Pair<String, Any?>): User?"))
    }

    @Test
    fun `RepositoryGenerator contains findAllWhere method`() {
        val entities = analyzeEntities("""
entity User:
  name is Text
""".trimIndent())

        val gen = RepositoryGenerator()
        val code = gen.generate(entities[0], "com.example.model", "com.example.db")

        assertTrue(code.contains("fun findAllWhere(vararg conditions: Pair<String, Any?>): List<User>"))
    }

    @Test
    fun `RepositoryGenerator contains create method`() {
        val entities = analyzeEntities("""
entity User:
  name is Text
  email is Text
""".trimIndent())

        val gen = RepositoryGenerator()
        val code = gen.generate(entities[0], "com.example.model", "com.example.db")

        assertTrue(code.contains("fun create(entity: User): User"))
        assertTrue(code.contains("INSERT INTO User (name, email) VALUES (?, ?)"))
    }

    @Test
    fun `RepositoryGenerator contains updateWhere method`() {
        val entities = analyzeEntities("""
entity User:
  name is Text
""".trimIndent())

        val gen = RepositoryGenerator()
        val code = gen.generate(entities[0], "com.example.model", "com.example.db")

        assertTrue(code.contains("fun updateWhere(assignments: Map<String, Any?>, vararg conditions: Pair<String, Any?>): Int"))
    }

    @Test
    fun `RepositoryGenerator contains deleteWhere method`() {
        val entities = analyzeEntities("""
entity User:
  name is Text
""".trimIndent())

        val gen = RepositoryGenerator()
        val code = gen.generate(entities[0], "com.example.model", "com.example.db")

        assertTrue(code.contains("fun deleteWhere(vararg conditions: Pair<String, Any?>): Int"))
    }

    @Test
    fun `RepositoryGenerator handles entity ref column names in SQL`() {
        val entities = analyzeEntities("""
entity User:
  name is Text

entity Post:
  title is Text
  author is User
""".trimIndent())

        val gen = RepositoryGenerator()
        val postEntity = entities.first { it.declaration.name == "Post" }
        val code = gen.generate(postEntity, "com.example.model", "com.example.db")

        // Should use author_id in SQL columns
        assertTrue(code.contains("id, title, author_id"))
        assertTrue(code.contains("INSERT INTO Post (title, author_id) VALUES (?, ?)"))
    }

    @Test
    fun `RepositoryGenerator contains mapRow method`() {
        val entities = analyzeEntities("""
entity User:
  name is Text
  email is Text
""".trimIndent())

        val gen = RepositoryGenerator()
        val code = gen.generate(entities[0], "com.example.model", "com.example.db")

        assertTrue(code.contains("private fun mapRow(rs: ResultSet): User"))
        assertTrue(code.contains("rs.getLong(\"id\")"))
        assertTrue(code.contains("rs.getString(\"name\")"))
        assertTrue(code.contains("rs.getString(\"email\")"))
    }

    // ---------------------------------------------------------------
    // 6. DatabaseInitGenerator tests
    // ---------------------------------------------------------------

    @Test
    fun `DatabaseInitGenerator produces PlaingDatabase object`() {
        val entities = analyzeEntities("""
entity User:
  name is Text
""".trimIndent())

        val gen = DatabaseInitGenerator()
        val code = gen.generate(entities, "com.example.db")

        assertTrue(code.contains("package com.example.db"))
        assertTrue(code.contains("object PlaingDatabase"))
        assertTrue(code.contains("import java.sql.Connection"))
        assertTrue(code.contains("import java.sql.DriverManager"))
    }

    @Test
    fun `DatabaseInitGenerator contains connect method with default url`() {
        val entities = analyzeEntities("""
entity User:
  name is Text
""".trimIndent())

        val gen = DatabaseInitGenerator()
        val code = gen.generate(entities, "com.example.db")

        assertTrue(code.contains("fun connect(url: String = \"jdbc:sqlite:plaing.db\"): Connection"))
        assertTrue(code.contains("DriverManager.getConnection(url)"))
        assertTrue(code.contains("createTables(conn)"))
    }

    @Test
    fun `DatabaseInitGenerator contains getConnection and close methods`() {
        val entities = analyzeEntities("""
entity User:
  name is Text
""".trimIndent())

        val gen = DatabaseInitGenerator()
        val code = gen.generate(entities, "com.example.db")

        assertTrue(code.contains("fun getConnection(): Connection"))
        assertTrue(code.contains("fun close()"))
    }

    @Test
    fun `DatabaseInitGenerator createTables includes all entities`() {
        val entities = analyzeEntities("""
entity User:
  name is Text

entity Post:
  title is Text
  author is User
""".trimIndent())

        val gen = DatabaseInitGenerator()
        val code = gen.generate(entities, "com.example.db")

        assertTrue(code.contains("CREATE TABLE IF NOT EXISTS User"))
        assertTrue(code.contains("CREATE TABLE IF NOT EXISTS Post"))
    }

    // ---------------------------------------------------------------
    // 7. Integration: CodeGenerator produces files on disk
    // ---------------------------------------------------------------

    @Test
    fun `CodeGenerator generates all expected files`() {
        val outputDir = createTempDir("plaing-codegen-test")
        try {
            val source = """
entity User:
  name is Text, required
  email is Text, required, unique

entity Post:
  title is Text, required
  body is Text
  author is User
""".trimIndent()

            val program = parseProgram(source)
            val generator = CodeGenerator(outputDir)
            val result = generator.generate(program)

            assertEquals(2, result.entityCount)
            // 2 data classes + 2 sql schemas + 2 repositories + 1 database init = 7
            assertEquals(7, result.generatedFiles.size)

            // Verify data class files exist
            assertTrue(result.generatedFiles.any { it.endsWith("User.kt") })
            assertTrue(result.generatedFiles.any { it.endsWith("Post.kt") })

            // Verify SQL files exist
            assertTrue(result.generatedFiles.any { it.endsWith("User.sql") })
            assertTrue(result.generatedFiles.any { it.endsWith("Post.sql") })

            // Verify repository files exist
            assertTrue(result.generatedFiles.any { it.endsWith("UserRepository.kt") })
            assertTrue(result.generatedFiles.any { it.endsWith("PostRepository.kt") })

            // Verify database init file exists
            assertTrue(result.generatedFiles.any { it.endsWith("PlaingDatabase.kt") })

            // Verify the files actually exist on disk
            for (path in result.generatedFiles) {
                assertTrue(File(path).exists(), "Expected file to exist: $path")
            }
        } finally {
            outputDir.deleteRecursively()
        }
    }

    @Test
    fun `CodeGenerator returns zero entity count for program with no entities`() {
        val outputDir = createTempDir("plaing-codegen-empty")
        try {
            val program = PlaingProgram(emptyList())
            val generator = CodeGenerator(outputDir)
            val result = generator.generate(program)

            assertEquals(0, result.entityCount)
            assertTrue(result.generatedFiles.isEmpty())
        } finally {
            outputDir.deleteRecursively()
        }
    }

    @Test
    fun `CodeGenerator generated data class file has correct content`() {
        val outputDir = createTempDir("plaing-codegen-content")
        try {
            val source = """
entity User:
  name is Text, required
  email is Text, required, unique
  role is Text, default "member"
""".trimIndent()

            val program = parseProgram(source)
            val generator = CodeGenerator(outputDir)
            val result = generator.generate(program)

            val userKtPath = result.generatedFiles.first { it.endsWith("User.kt") }
            val content = File(userKtPath).readText()

            assertTrue(content.contains("package dev.plaing.generated.model"))
            assertTrue(content.contains("data class User("))
            assertTrue(content.contains("val id: Long = 0,"))
            assertTrue(content.contains("val name: String,"))
            assertTrue(content.contains("val email: String,"))
            assertTrue(content.contains("val role: String = \"member\","))
        } finally {
            outputDir.deleteRecursively()
        }
    }

    // ---------------------------------------------------------------
    // 8. Integration: generate SQL, execute against in-memory SQLite
    // ---------------------------------------------------------------

    @Test
    fun `generated SQL creates tables in SQLite`() {
        val entities = analyzeEntities("""
entity User:
  name is Text, required
  email is Text, required, unique

entity Post:
  title is Text, required
  body is Text
  author is User
""".trimIndent())

        val sqlGen = SqlSchemaGenerator()

        val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        try {
            // Execute each CREATE TABLE statement
            for (entity in entities) {
                val sql = sqlGen.generateCreateTable(entity)
                conn.createStatement().execute(sql)
            }

            // Verify tables exist by querying sqlite_master
            val rs = conn.createStatement().executeQuery(
                "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name"
            )
            val tables = mutableListOf<String>()
            while (rs.next()) {
                tables.add(rs.getString("name"))
            }

            assertTrue("User" in tables, "User table should exist")
            assertTrue("Post" in tables, "Post table should exist")
        } finally {
            conn.close()
        }
    }

    @Test
    fun `generated SQL respects column constraints in SQLite`() {
        val entities = analyzeEntities("""
entity User:
  name is Text, required
  email is Text, required, unique
  bio is Optional Text
  role is Text, default "member"
""".trimIndent())

        val sqlGen = SqlSchemaGenerator()

        val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        try {
            val sql = sqlGen.generateCreateTable(entities[0])
            conn.createStatement().execute(sql)

            // Verify table info using PRAGMA
            val pragma = conn.createStatement().executeQuery("PRAGMA table_info(User)")
            val columns = mutableMapOf<String, Map<String, Any?>>()
            while (pragma.next()) {
                val colName = pragma.getString("name")
                columns[colName] = mapOf(
                    "type" to pragma.getString("type"),
                    "notnull" to pragma.getInt("notnull"),
                    "dflt_value" to pragma.getString("dflt_value"),
                )
            }

            // id column
            assertTrue("id" in columns)
            assertEquals("INTEGER", columns["id"]?.get("type"))

            // name: NOT NULL, no default
            assertTrue("name" in columns)
            assertEquals(1, columns["name"]?.get("notnull"))

            // email: NOT NULL
            assertTrue("email" in columns)
            assertEquals(1, columns["email"]?.get("notnull"))

            // bio: nullable (NOT NULL = 0)
            assertTrue("bio" in columns)
            assertEquals(0, columns["bio"]?.get("notnull"))

            // role: NOT NULL with default
            assertTrue("role" in columns)
            assertEquals(1, columns["role"]?.get("notnull"))
            assertEquals("'member'", columns["role"]?.get("dflt_value"))
        } finally {
            conn.close()
        }
    }

    // ---------------------------------------------------------------
    // 9. Integration: INSERT a row, SELECT it back
    // ---------------------------------------------------------------

    @Test
    fun `can insert and select a row using generated SQL`() {
        val entities = analyzeEntities("""
entity User:
  name is Text, required
  email is Text, required, unique
  role is Text, default "member"
""".trimIndent())

        val sqlGen = SqlSchemaGenerator()

        val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        try {
            // Create the table
            conn.createStatement().execute(sqlGen.generateCreateTable(entities[0]))

            // Insert a row
            val insertStmt = conn.prepareStatement(
                "INSERT INTO User (name, email) VALUES (?, ?)"
            )
            insertStmt.setString(1, "Alice")
            insertStmt.setString(2, "alice@example.com")
            insertStmt.executeUpdate()

            // Select it back
            val rs = conn.createStatement().executeQuery("SELECT * FROM User WHERE email = 'alice@example.com'")
            assertTrue(rs.next(), "Should find the inserted row")
            assertEquals("Alice", rs.getString("name"))
            assertEquals("alice@example.com", rs.getString("email"))
            assertEquals("member", rs.getString("role")) // default value
            assertTrue(rs.getLong("id") > 0, "id should be auto-incremented")
        } finally {
            conn.close()
        }
    }

    @Test
    fun `can insert and select with foreign key reference`() {
        val entities = analyzeEntities("""
entity User:
  name is Text, required

entity Post:
  title is Text, required
  body is Text
  author is User
""".trimIndent())

        val sqlGen = SqlSchemaGenerator()

        val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        try {
            // Enable foreign keys
            conn.createStatement().execute("PRAGMA foreign_keys = ON")

            // Create tables in topological order (User first, then Post)
            for (entity in entities) {
                conn.createStatement().execute(sqlGen.generateCreateTable(entity))
            }

            // Insert a user
            val userStmt = conn.prepareStatement(
                "INSERT INTO User (name) VALUES (?)",
                java.sql.Statement.RETURN_GENERATED_KEYS
            )
            userStmt.setString(1, "Alice")
            userStmt.executeUpdate()
            val userKeys = userStmt.generatedKeys
            assertTrue(userKeys.next())
            val userId = userKeys.getLong(1)

            // Insert a post referencing the user
            val postStmt = conn.prepareStatement(
                "INSERT INTO Post (title, body, author_id) VALUES (?, ?, ?)"
            )
            postStmt.setString(1, "First Post")
            postStmt.setString(2, "Hello world")
            postStmt.setLong(3, userId)
            postStmt.executeUpdate()

            // Select the post with a JOIN
            val rs = conn.createStatement().executeQuery(
                "SELECT Post.title, Post.body, User.name AS author_name FROM Post JOIN User ON Post.author_id = User.id"
            )
            assertTrue(rs.next(), "Should find the post")
            assertEquals("First Post", rs.getString("title"))
            assertEquals("Hello world", rs.getString("body"))
            assertEquals("Alice", rs.getString("author_name"))
        } finally {
            conn.close()
        }
    }

    @Test
    fun `can insert and select with optional fields`() {
        val entities = analyzeEntities("""
entity User:
  name is Text, required
  bio is Optional Text
""".trimIndent())

        val sqlGen = SqlSchemaGenerator()

        val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        try {
            conn.createStatement().execute(sqlGen.generateCreateTable(entities[0]))

            // Insert with bio = null
            val stmt1 = conn.prepareStatement("INSERT INTO User (name, bio) VALUES (?, ?)")
            stmt1.setString(1, "Alice")
            stmt1.setNull(2, java.sql.Types.VARCHAR)
            stmt1.executeUpdate()

            // Insert with bio = "Hello"
            val stmt2 = conn.prepareStatement("INSERT INTO User (name, bio) VALUES (?, ?)")
            stmt2.setString(1, "Bob")
            stmt2.setString(2, "Hello there")
            stmt2.executeUpdate()

            // Select Alice - bio should be null
            val rs1 = conn.createStatement().executeQuery("SELECT * FROM User WHERE name = 'Alice'")
            assertTrue(rs1.next())
            assertEquals(null, rs1.getString("bio"))

            // Select Bob - bio should be "Hello there"
            val rs2 = conn.createStatement().executeQuery("SELECT * FROM User WHERE name = 'Bob'")
            assertTrue(rs2.next())
            assertEquals("Hello there", rs2.getString("bio"))
        } finally {
            conn.close()
        }
    }

    @Test
    fun `unique constraint is enforced in SQLite`() {
        val entities = analyzeEntities("""
entity User:
  name is Text, required
  email is Text, required, unique
""".trimIndent())

        val sqlGen = SqlSchemaGenerator()

        val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        try {
            conn.createStatement().execute(sqlGen.generateCreateTable(entities[0]))

            // Insert first row
            val stmt1 = conn.prepareStatement("INSERT INTO User (name, email) VALUES (?, ?)")
            stmt1.setString(1, "Alice")
            stmt1.setString(2, "alice@example.com")
            stmt1.executeUpdate()

            // Try to insert duplicate email - should fail
            val stmt2 = conn.prepareStatement("INSERT INTO User (name, email) VALUES (?, ?)")
            stmt2.setString(1, "Bob")
            stmt2.setString(2, "alice@example.com")

            var thrown = false
            try {
                stmt2.executeUpdate()
            } catch (e: java.sql.SQLException) {
                thrown = true
                assertTrue(e.message?.contains("UNIQUE") == true || e.message?.contains("unique") == true,
                    "Exception should mention UNIQUE constraint: ${e.message}")
            }
            assertTrue(thrown, "Should have thrown SQLException for unique violation")
        } finally {
            conn.close()
        }
    }

    @Test
    fun `not null constraint is enforced in SQLite`() {
        val entities = analyzeEntities("""
entity User:
  name is Text, required
  email is Text, required
""".trimIndent())

        val sqlGen = SqlSchemaGenerator()

        val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        try {
            conn.createStatement().execute(sqlGen.generateCreateTable(entities[0]))

            // Try to insert with null for a NOT NULL column
            val stmt = conn.prepareStatement("INSERT INTO User (name, email) VALUES (?, ?)")
            stmt.setString(1, "Alice")
            stmt.setNull(2, java.sql.Types.VARCHAR)

            var thrown = false
            try {
                stmt.executeUpdate()
            } catch (e: java.sql.SQLException) {
                thrown = true
                assertTrue(e.message?.contains("NOT NULL") == true || e.message?.contains("not null") == true,
                    "Exception should mention NOT NULL constraint: ${e.message}")
            }
            assertTrue(thrown, "Should have thrown SQLException for NOT NULL violation")
        } finally {
            conn.close()
        }
    }

    // ---------------------------------------------------------------
    // 10. EventGen tests
    // ---------------------------------------------------------------

    @Test
    fun `EventGen eventClassName converts SCREAMING_SNAKE_CASE to PascalCase plus Event`() {
        assertEquals("LoginAttemptEvent", EventGen.eventClassName("LOGIN_ATTEMPT"))
        assertEquals("LoginSuccessEvent", EventGen.eventClassName("LOGIN_SUCCESS"))
        assertEquals("UserCreatedEvent", EventGen.eventClassName("USER_CREATED"))
        assertEquals("PasswordResetRequestedEvent", EventGen.eventClassName("PASSWORD_RESET_REQUESTED"))
    }

    @Test
    fun `EventGen generates event class with Serializable and fields`() {
        val source = """
event LOGIN_ATTEMPT:
  carries email as Text, password as Text
""".trimIndent()
        val eventGen = EventGen()
        val tokens = Lexer(source, "test.plaing").tokenize()
        val program = Parser(tokens, "test.plaing").parse()
        val event = program.declarations.filterIsInstance<EventDeclaration>().first()
        val code = eventGen.generate(event, "dev.plaing.generated.event")

        assertTrue(code.contains("@Serializable"))
        assertTrue(code.contains("data class LoginAttemptEvent("))
        assertTrue(code.contains("val email: String,"))
        assertTrue(code.contains("val password: String,"))
        assertTrue(code.contains("const val EVENT_NAME = \"LOGIN_ATTEMPT\""))
        assertTrue(code.contains("fun fromPayload(payload: JsonObject): LoginAttemptEvent"))
        assertTrue(code.contains("fun toPayload(): JsonObject"))
    }

    @Test
    fun `EventGen generates class not data class when event has no fields`() {
        // Construct an EventDeclaration with no fields directly, since the parser
        // always expects at least one field after 'carries'
        val loc = SourceLocation(1, 1, "test.plaing")
        val event = EventDeclaration("SYSTEM_SHUTDOWN", emptyList(), loc)
        val eventGen = EventGen()
        val code = eventGen.generate(event, "dev.plaing.generated.event")

        assertTrue(code.contains("class SystemShutdownEvent {"))
        assertFalse(code.contains("data class SystemShutdownEvent"))
    }

    @Test
    fun `EventGen fromPayload extracts fields by type`() {
        val source = """
event USER_UPDATE:
  carries name as Text, score as Number, active as Boolean
""".trimIndent()
        val eventGen = EventGen()
        val tokens = Lexer(source, "test.plaing").tokenize()
        val program = Parser(tokens, "test.plaing").parse()
        val event = program.declarations.filterIsInstance<EventDeclaration>().first()
        val code = eventGen.generate(event, "dev.plaing.generated.event")

        assertTrue(code.contains("payload[\"name\"]?.jsonPrimitive?.content"))
        assertTrue(code.contains("payload[\"score\"]?.jsonPrimitive?.double"))
        assertTrue(code.contains("payload[\"active\"]?.jsonPrimitive?.boolean"))
    }

    // ---------------------------------------------------------------
    // 11. HandlerGen tests
    // ---------------------------------------------------------------

    @Test
    fun `HandlerGen generates handler class implementing EventHandler`() {
        val source = """
event LOGIN_ATTEMPT:
  carries email as Text, password as Text

handle LOGIN_ATTEMPT:
  find User where email is LOGIN_ATTEMPT.email
""".trimIndent()
        val handlerGen = HandlerGen()
        val tokens = Lexer(source, "test.plaing").tokenize()
        val program = Parser(tokens, "test.plaing").parse()
        val handler = program.declarations.filterIsInstance<HandlerDeclaration>().first()
        val events = program.declarations.filterIsInstance<EventDeclaration>().associateBy { it.name }
        val code = handlerGen.generate(handler, events, "dev.plaing.generated.handler")

        assertTrue(code.contains("class LoginAttemptHandler("))
        assertTrue(code.contains(") : EventHandler {"))
        assertTrue(code.contains("override suspend fun handle(envelope: EventEnvelope): HandlerResult"))
    }

    @Test
    fun `HandlerGen generates correct SQL for find statement`() {
        val source = """
event LOGIN_ATTEMPT:
  carries email as Text

handle LOGIN_ATTEMPT:
  find User where email is LOGIN_ATTEMPT.email
""".trimIndent()
        val handlerGen = HandlerGen()
        val tokens = Lexer(source, "test.plaing").tokenize()
        val program = Parser(tokens, "test.plaing").parse()
        val handler = program.declarations.filterIsInstance<HandlerDeclaration>().first()
        val events = program.declarations.filterIsInstance<EventDeclaration>().associateBy { it.name }
        val code = handlerGen.generate(handler, events, "dev.plaing.generated.handler")

        assertTrue(code.contains("SELECT * FROM User WHERE email = ?"))
        assertTrue(code.contains(".setObject(1, event.email)"))
    }

    @Test
    fun `HandlerGen generates if-else blocks`() {
        val source = """
event LOGIN_ATTEMPT:
  carries email as Text, password as Text

handle LOGIN_ATTEMPT:
  find User where email is LOGIN_ATTEMPT.email
  if no User found:
    stop
  otherwise:
    emit LOGIN_SUCCESS with user_id = User.id
""".trimIndent()
        val handlerGen = HandlerGen()
        val tokens = Lexer(source, "test.plaing").tokenize()
        val program = Parser(tokens, "test.plaing").parse()
        val handler = program.declarations.filterIsInstance<HandlerDeclaration>().first()
        val events = program.declarations.filterIsInstance<EventDeclaration>().associateBy { it.name }
        val code = handlerGen.generate(handler, events, "dev.plaing.generated.handler")

        assertTrue(code.contains("if (user == null)"))
        assertTrue(code.contains("} else {"))
    }

    @Test
    fun `HandlerGen generates emit statements`() {
        val source = """
event LOGIN_ATTEMPT:
  carries email as Text

event LOGIN_SUCCESS:
  carries user_id as Number

handle LOGIN_ATTEMPT:
  find User where email is LOGIN_ATTEMPT.email
  emit LOGIN_SUCCESS with user_id = User.id
""".trimIndent()
        val handlerGen = HandlerGen()
        val tokens = Lexer(source, "test.plaing").tokenize()
        val program = Parser(tokens, "test.plaing").parse()
        val handler = program.declarations.filterIsInstance<HandlerDeclaration>().first()
        val events = program.declarations.filterIsInstance<EventDeclaration>().associateBy { it.name }
        val code = handlerGen.generate(handler, events, "dev.plaing.generated.handler")

        assertTrue(code.contains("emitted.add(EventEnvelope("))
        assertTrue(code.contains("event = \"LOGIN_SUCCESS\""))
        assertTrue(code.contains("buildJsonObject {"))
    }

    @Test
    fun `HandlerGen generates stop as early return`() {
        val source = """
event LOGIN_ATTEMPT:
  carries email as Text

handle LOGIN_ATTEMPT:
  find User where email is LOGIN_ATTEMPT.email
  if no User found:
    stop
""".trimIndent()
        val handlerGen = HandlerGen()
        val tokens = Lexer(source, "test.plaing").tokenize()
        val program = Parser(tokens, "test.plaing").parse()
        val handler = program.declarations.filterIsInstance<HandlerDeclaration>().first()
        val events = program.declarations.filterIsInstance<EventDeclaration>().associateBy { it.name }
        val code = handlerGen.generate(handler, events, "dev.plaing.generated.handler")

        // stop should generate a return statement
        assertTrue(code.contains("return HandlerResult(emitted)"))
    }

    @Test
    fun `HandlerGen uses unique stmt variable names to avoid collisions`() {
        val source = """
event CREATE_NOTE:
  carries title as Text, body as Text

event NOTES_UPDATED:
  carries notes as Text

handle CREATE_NOTE:
  create Note with title = CREATE_NOTE.title, body = CREATE_NOTE.body
  find all Note
  emit NOTES_UPDATED with notes = Note
""".trimIndent()
        val handlerGen = HandlerGen()
        val tokens = Lexer(source, "test.plaing").tokenize()
        val program = Parser(tokens, "test.plaing").parse()
        val handler = program.declarations.filterIsInstance<HandlerDeclaration>().first()
        val events = program.declarations.filterIsInstance<EventDeclaration>().associateBy { it.name }
        val code = handlerGen.generate(handler, events, "dev.plaing.generated.handler")

        // Should have two different stmt variables (stmt1, stmt2)
        assertTrue(code.contains("val stmt1 = db.prepareStatement("), "Expected stmt1 for create: $code")
        assertTrue(code.contains("val stmt2 = db.prepareStatement("), "Expected stmt2 for find: $code")
        // Should NOT have duplicate noteStmt
        assertFalse(code.contains("val noteStmt"), "Should not use noteStmt pattern: $code")
    }

    @Test
    fun `HandlerGen emits find all results as JsonArray`() {
        val source = """
event LOAD_NOTES:
  carries dummy as Text

event NOTES_LOADED:
  carries notes as Text

handle LOAD_NOTES:
  find all Note
  emit NOTES_LOADED with notes = Note
""".trimIndent()
        val handlerGen = HandlerGen()
        val tokens = Lexer(source, "test.plaing").tokenize()
        val program = Parser(tokens, "test.plaing").parse()
        val handler = program.declarations.filterIsInstance<HandlerDeclaration>().first()
        val events = program.declarations.filterIsInstance<EventDeclaration>().associateBy { it.name }
        val code = handlerGen.generate(handler, events, "dev.plaing.generated.handler")

        // find all should produce noteList
        assertTrue(code.contains("noteList"), "Expected noteList variable: $code")
        // emit should serialize as JsonArray
        assertTrue(code.contains("JsonArray(noteList"), "Expected JsonArray serialization: $code")
        assertTrue(code.contains("mapToJsonObject"), "Expected mapToJsonObject helper: $code")
    }

    @Test
    fun `HandlerGen emits single entity as JsonObject`() {
        val source = """
event CREATE_NOTE:
  carries title as Text

event NOTE_CREATED:
  carries note as Text

handle CREATE_NOTE:
  create Note with title = CREATE_NOTE.title
  emit NOTE_CREATED with note = Note
""".trimIndent()
        val handlerGen = HandlerGen()
        val tokens = Lexer(source, "test.plaing").tokenize()
        val program = Parser(tokens, "test.plaing").parse()
        val handler = program.declarations.filterIsInstance<HandlerDeclaration>().first()
        val events = program.declarations.filterIsInstance<EventDeclaration>().associateBy { it.name }
        val code = handlerGen.generate(handler, events, "dev.plaing.generated.handler")

        // emit should serialize single entity as JsonObject
        assertTrue(code.contains("mapToJsonObject(it)"), "Expected mapToJsonObject for single entity: $code")
        assertFalse(code.contains("JsonPrimitive(note"), "Should not use JsonPrimitive for entity: $code")
    }

    // ---------------------------------------------------------------
    // EventGen List type tests
    // ---------------------------------------------------------------

    @Test
    fun `EventGen handles List of Entity type`() {
        val program = parseProgram("""
event NOTES_LOADED:
  carries notes as List of Note
""".trimIndent())
        val event = program.declarations.filterIsInstance<EventDeclaration>().first()
        val eventGen = EventGen()
        val code = eventGen.generate(event, "dev.plaing.generated.event")

        assertTrue(code.contains("List<JsonObject>"), "Expected List<JsonObject> type: $code")
        assertTrue(code.contains("jsonArray"), "Expected jsonArray extractor: $code")
        assertFalse(code.contains("List<Long>"), "Should not be List<Long>: $code")
    }

    @Test
    fun `EventGen handles entity ref as JsonObject`() {
        val program = parseProgram("""
event NOTE_CREATED:
  carries note as Note
""".trimIndent())
        val event = program.declarations.filterIsInstance<EventDeclaration>().first()
        val eventGen = EventGen()
        val code = eventGen.generate(event, "dev.plaing.generated.event")

        assertTrue(code.contains("JsonObject"), "Expected JsonObject type: $code")
        assertTrue(code.contains("jsonObject"), "Expected jsonObject extractor: $code")
    }

    // ---------------------------------------------------------------
    // 12. Integration: full pipeline with entity + event + handler
    // ---------------------------------------------------------------

    @Test
    fun `CodeGenerator full pipeline generates entity event and handler files`() {
        val outputDir = createTempDir("plaing-codegen-phase3")
        try {
            val source = """
entity User:
  name is Text, required
  email is Text, required, unique

event LOGIN_ATTEMPT:
  carries email as Text, password as Text

handle LOGIN_ATTEMPT:
  find User where email is LOGIN_ATTEMPT.email
  if no User found:
    stop
""".trimIndent()

            val program = parseProgram(source)
            val generator = CodeGenerator(outputDir)
            val result = generator.generate(program)

            assertEquals(1, result.entityCount)

            // Entity files: data class + sql + repository + database init = 4
            assertTrue(result.generatedFiles.any { it.endsWith("User.kt") && it.contains("model") })
            assertTrue(result.generatedFiles.any { it.endsWith("User.sql") })
            assertTrue(result.generatedFiles.any { it.endsWith("UserRepository.kt") })
            assertTrue(result.generatedFiles.any { it.endsWith("PlaingDatabase.kt") })

            // Event file
            assertTrue(result.generatedFiles.any { it.endsWith("LoginAttemptEvent.kt") })

            // Handler file
            assertTrue(result.generatedFiles.any { it.endsWith("LoginAttemptHandler.kt") })

            // ServerApp.kt
            assertTrue(result.generatedFiles.any { it.endsWith("ServerApp.kt") })

            // Verify all files exist on disk
            for (path in result.generatedFiles) {
                assertTrue(File(path).exists(), "Expected file to exist: $path")
            }
        } finally {
            outputDir.deleteRecursively()
        }
    }

    // ---------------------------------------------------------------
    // 13. PageGen tests
    // ---------------------------------------------------------------

    @Test
    fun `PageGen generates Composable function with correct name`() {
        val source = """
page LoginPage:
  layout centered:
    heading "Welcome"
""".trimIndent()
        val program = parseProgram(source)
        val pageGen = PageGen()
        val page = program.declarations.filterIsInstance<PageDeclaration>().first()
        val code = pageGen.generate(page, "dev.plaing.generated.ui")

        assertTrue(code.contains("@Composable"))
        assertTrue(code.contains("fun LoginPage("))
        assertTrue(code.contains("stateStore: StateStore"))
        assertTrue(code.contains("onEvent: (String, kotlinx.serialization.json.JsonObject) -> Unit"))
    }

    @Test
    fun `PageGen bound fields produce remember mutableStateOf state variables`() {
        val source = """
page LoginPage:
  layout centered:
    form login-form:
      input email_input: placeholder "Email", binds to email
      input password_input: placeholder "Password", type secret, binds to password
""".trimIndent()
        val program = parseProgram(source)
        val pageGen = PageGen()
        val page = program.declarations.filterIsInstance<PageDeclaration>().first()
        val code = pageGen.generate(page, "dev.plaing.generated.ui")

        assertTrue(code.contains("var email by remember { mutableStateOf(\"\") }"),
            "Should generate state variable for email binding")
        assertTrue(code.contains("var password by remember { mutableStateOf(\"\") }"),
            "Should generate state variable for password binding")
    }

    @Test
    fun `PageGen button with action generates onEvent call with buildJsonObject`() {
        val source = """
page LoginPage:
  layout centered:
    form login-form:
      input email_input: placeholder "Email", binds to email
      button "Log In": emits LOGIN_ATTEMPT with email, password
""".trimIndent()
        val program = parseProgram(source)
        val pageGen = PageGen()
        val page = program.declarations.filterIsInstance<PageDeclaration>().first()
        val code = pageGen.generate(page, "dev.plaing.generated.ui")

        assertTrue(code.contains("onEvent(\"LOGIN_ATTEMPT\", buildJsonObject {"),
            "Should generate onEvent call with event name")
        assertTrue(code.contains("put(\"email\", email)"),
            "Should put email field in JSON payload")
    }

    // ---------------------------------------------------------------
    // 14. StyleGen tests
    // ---------------------------------------------------------------

    @Test
    fun `StyleGen generates Modifier extension function with correct name`() {
        val loc = SourceLocation(1, 1, "test.plaing")
        val style = StyleDeclaration(
            "login-form",
            listOf(StyleProperty("padding", "24px", loc)),
            emptyList(),
            loc
        )
        val styleGen = StyleGen()
        val code = styleGen.generate(style, "dev.plaing.generated.style")

        assertTrue(code.contains("fun Modifier.loginFormStyle(): Modifier = this"),
            "Should convert login-form to loginFormStyle as Modifier extension")
    }

    @Test
    fun `StyleGen maps background color border radius and padding to correct modifiers`() {
        val loc = SourceLocation(1, 1, "test.plaing")
        val style = StyleDeclaration(
            "card",
            listOf(
                StyleProperty("background color", "white", loc),
                StyleProperty("border radius", "8px", loc),
                StyleProperty("padding", "24px", loc),
            ),
            emptyList(),
            loc
        )
        val styleGen = StyleGen()
        val code = styleGen.generate(style, "dev.plaing.generated.style")

        assertTrue(code.contains(".background(Color.White)"),
            "Should map background color white to Color.White")
        assertTrue(code.contains(".clip(RoundedCornerShape(8.dp))"),
            "Should map border radius to RoundedCornerShape")
        assertTrue(code.contains(".padding(24.dp)"),
            "Should map padding 24px to 24.dp")
    }

    @Test
    fun `StyleGen maps color names to correct Color values`() {
        val loc = SourceLocation(1, 1, "test.plaing")
        val style = StyleDeclaration(
            "test-style",
            listOf(
                StyleProperty("background color", "white", loc),
            ),
            emptyList(),
            loc
        )
        val styleGen = StyleGen()
        val code = styleGen.generate(style, "dev.plaing.generated.style")
        assertTrue(code.contains("Color.White"), "white should map to Color.White")

        val style2 = StyleDeclaration(
            "test-style2",
            listOf(
                StyleProperty("background color", "primary", loc),
            ),
            emptyList(),
            loc
        )
        val code2 = styleGen.generate(style2, "dev.plaing.generated.style")
        assertTrue(code2.contains("Color(0xFF6200EE)"), "primary should map to Color(0xFF6200EE)")
    }

    // ---------------------------------------------------------------
    // 15. ReactionGen tests
    // ---------------------------------------------------------------

    @Test
    fun `ReactionGen generates handleReaction function with when block`() {
        val loc = SourceLocation(1, 1, "test.plaing")
        val reactions = listOf(
            ReactionDeclaration(
                "LOGIN_SUCCESS",
                listOf(NavigateAction("DashboardPage", loc)),
                loc
            )
        )
        val reactionGen = ReactionGen()
        val code = reactionGen.generate(reactions, "dev.plaing.generated.reaction")

        assertTrue(code.contains("fun handleReaction(envelope: EventEnvelope, stateStore: StateStore)"),
            "Should generate handleReaction function")
        assertTrue(code.contains("when (envelope.event)"),
            "Should generate when block on envelope.event")
        assertTrue(code.contains("\"LOGIN_SUCCESS\" -> {"),
            "Should match on event name")
    }

    @Test
    fun `ReactionGen generates correct code for store navigate and show alert actions`() {
        val loc = SourceLocation(1, 1, "test.plaing")
        val reactions = listOf(
            ReactionDeclaration(
                "LOGIN_SUCCESS",
                listOf(
                    StoreAction("User", DotAccess(Identifier("response", loc), "user", loc), loc),
                    NavigateAction("DashboardPage", loc),
                    ShowAlertAction(StringLiteral("Welcome back!", loc), "", loc),
                ),
                loc
            )
        )
        val reactionGen = ReactionGen()
        val code = reactionGen.generate(reactions, "dev.plaing.generated.reaction")

        assertTrue(code.contains("stateStore.storeEntity(\"User\""),
            "Should generate storeEntity call")
        assertTrue(code.contains("stateStore.navigateTo(\"DashboardPage\")"),
            "Should generate navigateTo call")
        assertTrue(code.contains("stateStore.showAlert(\"Welcome back!\")"),
            "Should generate showAlert call")
    }

    // ---------------------------------------------------------------
    // 16. ClientAppGen test
    // ---------------------------------------------------------------

    @Test
    fun `ClientAppGen generates main with Window page routing and WsClient`() {
        val loc = SourceLocation(1, 1, "test.plaing")
        val pages = listOf(
            PageDeclaration("LoginPage", listOf(HeadingElement("Login", loc)), loc),
            PageDeclaration("DashboardPage", listOf(HeadingElement("Dashboard", loc)), loc),
        )
        val reactions = listOf(
            ReactionDeclaration("LOGIN_SUCCESS", listOf(NavigateAction("DashboardPage", loc)), loc)
        )
        val clientAppGen = ClientAppGen()
        val code = clientAppGen.generate(pages, reactions, "dev.plaing.generated")

        assertTrue(code.contains("fun main() = application {"),
            "Should generate main function")
        assertTrue(code.contains("Window("),
            "Should generate Window composable")
        assertTrue(code.contains("WsClient"),
            "Should reference WsClient")
        assertTrue(code.contains("\"LoginPage\" -> LoginPage(stateStore, sendEvent)"),
            "Should route to LoginPage")
        assertTrue(code.contains("\"DashboardPage\" -> DashboardPage(stateStore, sendEvent)"),
            "Should route to DashboardPage")
    }

    // ---------------------------------------------------------------
    // 17. Full pipeline: entity + event + handler + page + reaction + style
    // ---------------------------------------------------------------

    @Test
    fun `CodeGenerator full pipeline generates all Phase 4 files`() {
        val outputDir = createTempDir("plaing-codegen-phase4")
        try {
            val source = """
entity User:
  name is Text, required
  email is Text, required, unique

event LOGIN_ATTEMPT:
  carries email as Text, password as Text

event LOGIN_SUCCESS:
  carries user_id as Number

handle LOGIN_ATTEMPT:
  find User where email is LOGIN_ATTEMPT.email
  if no User found:
    stop
  otherwise:
    emit LOGIN_SUCCESS with user_id = User.id

page LoginPage:
  layout centered:
    heading "Welcome"
    form login-form:
      input email_input: placeholder "Email", binds to email
      input password_input: placeholder "Password", type secret, binds to password
      button "Log In": emits LOGIN_ATTEMPT with email, password

page DashboardPage:
  layout centered:
    heading "Dashboard"

on LOGIN_SUCCESS:
  store User from LOGIN_SUCCESS.user
  navigate to DashboardPage

style login-form:
  background color is white
  border radius is 8px
  padding is 24px
""".trimIndent()

            val program = parseProgram(source)
            val generator = CodeGenerator(outputDir)
            val result = generator.generate(program)

            // Entity files: data class + sql + repository + database init = 4
            assertTrue(result.generatedFiles.any { it.endsWith("User.kt") && it.contains("model") })
            assertTrue(result.generatedFiles.any { it.endsWith("User.sql") })
            assertTrue(result.generatedFiles.any { it.endsWith("UserRepository.kt") })
            assertTrue(result.generatedFiles.any { it.endsWith("PlaingDatabase.kt") })

            // Event files: 2
            assertTrue(result.generatedFiles.any { it.endsWith("LoginAttemptEvent.kt") })
            assertTrue(result.generatedFiles.any { it.endsWith("LoginSuccessEvent.kt") })

            // Handler file: 1
            assertTrue(result.generatedFiles.any { it.endsWith("LoginAttemptHandler.kt") })

            // ServerApp.kt: 1
            assertTrue(result.generatedFiles.any { it.endsWith("ServerApp.kt") })

            // Page files: 2
            assertTrue(result.generatedFiles.any { it.endsWith("LoginPage.kt") })
            assertTrue(result.generatedFiles.any { it.endsWith("DashboardPage.kt") })

            // Style file: 1
            assertTrue(result.generatedFiles.any { it.contains("style") && it.endsWith("Style.kt") })

            // Reaction file: 1
            assertTrue(result.generatedFiles.any { it.endsWith("Reactions.kt") })

            // ClientApp.kt: 1
            assertTrue(result.generatedFiles.any { it.endsWith("ClientApp.kt") })

            // Total: 4 + 2 + 1 + 1 + 2 + 1 + 1 + 1 = 13 minimum, but could be more
            // The key assertion is that we have at least 13 generated files
            assertTrue(result.generatedFiles.size >= 13,
                "Expected at least 13 generated files, got ${result.generatedFiles.size}: ${result.generatedFiles}")

            // Verify all files exist on disk
            for (path in result.generatedFiles) {
                assertTrue(File(path).exists(), "Expected file to exist: $path")
            }
        } finally {
            outputDir.deleteRecursively()
        }
    }

    // ---------------------------------------------------------------
    // 14. Text and List element codegen tests
    // ---------------------------------------------------------------

    @Test
    fun `PageGen generates text element with string literal`() {
        val program = parseProgram("""
page TestPage:
  layout main:
    text "Hello World"
""".trimIndent())
        val page = program.declarations[0] as PageDeclaration

        val pageGen = PageGen()
        val code = pageGen.generate(page, "dev.plaing.generated.ui")
        assertTrue(code.contains("PlaingText(\"Hello World\")"))
    }

    @Test
    fun `PageGen generates text element with entity reference`() {
        val program = parseProgram("""
page TestPage:
  layout main:
    text User.name
""".trimIndent())
        val page = program.declarations[0] as PageDeclaration

        val pageGen = PageGen()
        val code = pageGen.generate(page, "dev.plaing.generated.ui")
        assertTrue(code.contains("PlaingText("))
        assertTrue(code.contains("stateStore.getEntity(\"User\")"))
        assertTrue(code.contains("\"name\""))
    }

    @Test
    fun `PageGen generates list element`() {
        val program = parseProgram("""
page TestPage:
  layout main:
    list notes: each Note show Note.title, Note.body
""".trimIndent())
        val page = program.declarations[0] as PageDeclaration

        val pageGen = PageGen()
        val code = pageGen.generate(page, "dev.plaing.generated.ui")
        assertTrue(code.contains("stateStore.getEntityList(\"Note\")"))
        assertTrue(code.contains("LazyColumn"))
        assertTrue(code.contains("PlaingListItem"))
        assertTrue(code.contains("\"title\""))
        assertTrue(code.contains("\"body\""))
    }

    @Test
    fun `ReactionGen generates store all action`() {
        val program = parseProgram("""
on NOTES_LOADED:
  store all Notes from NOTES_LOADED.notes
""".trimIndent())
        val reaction = program.declarations[0] as ReactionDeclaration

        val reactionGen = ReactionGen()
        val code = reactionGen.generate(listOf(reaction), "dev.plaing.generated.reaction")
        assertTrue(code.contains("storeEntityList(\"Notes\""))
        assertTrue(code.contains("jsonArray"))
    }

    @Test
    fun `PageGen generates imports for list and json`() {
        val program = parseProgram("""
page TestPage:
  layout main:
    list items: each Note show Note.title
""".trimIndent())
        val page = program.declarations[0] as PageDeclaration

        val pageGen = PageGen()
        val code = pageGen.generate(page, "dev.plaing.generated.ui")
        assertTrue(code.contains("import androidx.compose.foundation.lazy.*"))
        assertTrue(code.contains("import kotlinx.serialization.json.jsonPrimitive"))
    }

    // ---------------------------------------------------------------
    // Style wiring: styles apply to matching page elements
    // ---------------------------------------------------------------

    @Test
    fun `PageGen applies matching style to form element`() {
        val program = parseProgram("""
page LoginPage:
  layout main:
    form login-form:
      input email: placeholder "Email", binds to email
      button "Sign In": emits LOGIN_ATTEMPT with email

style login-form:
  background color is white
  border radius is 8px
""".trimIndent())
        val page = program.declarations[0] as PageDeclaration
        val styles = program.declarations.filterIsInstance<StyleDeclaration>()
        val styleNames = styles.map { it.targetName }.toSet()

        val pageGen = PageGen()
        val code = pageGen.generate(page, "dev.plaing.generated.ui", styleNames)
        assertTrue(code.contains(".loginFormStyle()"), "Form should have loginFormStyle() applied")
        assertTrue(code.contains("import dev.plaing.generated.style.*"), "Should import style package")
    }

    @Test
    fun `PageGen applies matching style to layout element`() {
        val program = parseProgram("""
page HomePage:
  layout main:
    heading "Hello"

style main:
  padding is 24px
""".trimIndent())
        val page = program.declarations[0] as PageDeclaration
        val styles = program.declarations.filterIsInstance<StyleDeclaration>()
        val styleNames = styles.map { it.targetName }.toSet()

        val pageGen = PageGen()
        val code = pageGen.generate(page, "dev.plaing.generated.ui", styleNames)
        assertTrue(code.contains(".mainStyle()"), "Layout should have mainStyle() applied")
    }

    @Test
    fun `PageGen does not apply style when no matching style exists`() {
        val program = parseProgram("""
page LoginPage:
  layout main:
    form login-form:
      input email: placeholder "Email", binds to email
""".trimIndent())
        val page = program.declarations[0] as PageDeclaration

        val pageGen = PageGen()
        val code = pageGen.generate(page, "dev.plaing.generated.ui", emptySet())
        assertFalse(code.contains("Style()"), "No style functions should be applied")
        assertFalse(code.contains("import dev.plaing.generated.style.*"), "Should not import style package")
    }

    // ---------------------------------------------------------------
    // Select + Edit: list click + input fills from
    // ---------------------------------------------------------------

    @Test
    fun `PageGen generates clickable list items with on click select`() {
        val program = parseProgram("""
page NotesPage:
  layout main:
    list notes: each Note show Note.title, Note.body, on click select Note
""".trimIndent())
        val page = program.declarations[0] as PageDeclaration
        val pageGen = PageGen()
        val code = pageGen.generate(page, "dev.plaing.generated.ui")
        assertTrue(code.contains("onClick = { stateStore.selectEntity(\"Note\", item) }"), "List item should call selectEntity on click")
    }

    @Test
    fun `PageGen generates list without onClick when no select`() {
        val program = parseProgram("""
page NotesPage:
  layout main:
    list notes: each Note show Note.title
""".trimIndent())
        val page = program.declarations[0] as PageDeclaration
        val pageGen = PageGen()
        val code = pageGen.generate(page, "dev.plaing.generated.ui")
        assertFalse(code.contains("onClick"), "List item should not have onClick without select")
    }

    @Test
    fun `PageGen generates input with fills from`() {
        val program = parseProgram("""
page EditPage:
  layout main:
    form edit-form:
      input title: placeholder "Title", binds to title, fills from Note.title
""".trimIndent())
        val page = program.declarations[0] as PageDeclaration
        val pageGen = PageGen()
        val code = pageGen.generate(page, "dev.plaing.generated.ui")
        assertTrue(code.contains("LaunchedEffect(stateStore.getSelectedEntity(\"Note\"))"), "Should use LaunchedEffect for fills from")
        assertTrue(code.contains("selected[\"title\"]?.jsonPrimitive?.content"), "Should read field from selected entity")
    }

    @Test
    fun `PageGen generates full select and edit page`() {
        val program = parseProgram("""
page NotesPage:
  layout main:
    list notes: each Note show Note.title, on click select Note
    form edit-form:
      input title: placeholder "Title", binds to title, fills from Note.title
      button "Save": emits UPDATE_NOTE with title
""".trimIndent())
        val page = program.declarations[0] as PageDeclaration
        val pageGen = PageGen()
        val code = pageGen.generate(page, "dev.plaing.generated.ui")
        assertTrue(code.contains("selectEntity"), "Should have selectEntity call")
        assertTrue(code.contains("getSelectedEntity"), "Should have getSelectedEntity call")
        assertTrue(code.contains("var title by remember"), "Should have bound field variable")
    }

    // ---------------------------------------------------------------
    // Conditional button: if Entity selected emits X otherwise emits Y
    // ---------------------------------------------------------------

    @Test
    fun `PageGen generates conditional button with if selected`() {
        val program = parseProgram("""
page NotesPage:
  layout main:
    form note-form:
      input title: placeholder "Title", binds to title
      button "Save": if Note selected emits UPDATE_NOTE with title otherwise emits CREATE_NOTE with title
""".trimIndent())
        val page = program.declarations[0] as PageDeclaration
        val pageGen = PageGen()
        val code = pageGen.generate(page, "dev.plaing.generated.ui")
        assertTrue(code.contains("getSelectedEntity(\"Note\") != null"), "Should check for selected entity")
        assertTrue(code.contains("\"UPDATE_NOTE\""), "Should emit UPDATE_NOTE when selected")
        assertTrue(code.contains("\"CREATE_NOTE\""), "Should emit CREATE_NOTE otherwise")
    }

    @Test
    fun `PageGen generates non-conditional button normally`() {
        val program = parseProgram("""
page LoginPage:
  layout main:
    button "Login": emits LOGIN_ATTEMPT with email
""".trimIndent())
        val page = program.declarations[0] as PageDeclaration
        val pageGen = PageGen()
        val code = pageGen.generate(page, "dev.plaing.generated.ui")
        assertTrue(code.contains("\"LOGIN_ATTEMPT\""))
        assertFalse(code.contains("getSelectedEntity"), "Should not have conditional logic")
    }

    @Suppress("DEPRECATION")
    private fun createTempDir(prefix: String): File = kotlin.io.createTempDir(prefix)
}
