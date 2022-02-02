import org.junit.jupiter.api.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.utility.DockerImageName
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.sql.Connection
import kotlin.random.Random
import kotlin.system.measureTimeMillis

class HugeQueryTest {
    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    @Test
    fun queryTest() {
        val excluded = prepareData()

        MySQLContainer(DockerImageName.parse("mysql"))
            .withConfigurationOverride("conf")
            .withUrlParam("allowLoadLocalInfile", "true")
            .withCreateContainerCmdModifier {
                it.hostConfig!!
                    .withMemory(512 * 1024 * 1024)
                    .withMemorySwap(1024 * 1024 * 1024)
                    .withCpuCount(4)
            }
            .withLogConsumer(Slf4jLogConsumer(logger))
            .use { mysql ->
                mysql.start()

                val connection = mysql.createConnection("")
                prepareDb(connection)

                val statement = connection.createStatement()
                println("Execute SELECT")
                val executionMillis = measureTimeMillis {
                    statement.execute("SELECT * FROM $RELATIONS_TABLE WHERE NOT product_id1 IN (${excluded.joinToString(",")});")
                }

                val rows = mutableListOf<Int>()
                val fetchMillis = measureTimeMillis {
                    while (statement.resultSet.next()) {
                        rows.add(statement.resultSet.getInt(1))
                    }
                }

                println("ResultSet size: ${rows.size}")
                println("Execution time: $executionMillis ms")
                println("Fetch time: $fetchMillis ms")
            }
    }

    private fun prepareData(): List<Int> {
        val idSet = mutableSetOf<Int>()
        while (idSet.size < IDS_COUNT) {
            idSet.add(Random.nextInt(10_000, Int.MAX_VALUE))
        }
        val idList = idSet.toList().shuffled()
        val file = File(FILENAME)
        file.setReadable(true, false)
        val fw = FileWriter(file.absoluteFile)
        val bw = BufferedWriter(fw)
        idList.forEach {
            bw.write("$it,${idList.random()}")
            bw.newLine()
        }
        bw.close()

        return idList.shuffled().take(EXCLUDE)
    }

    private fun prepareDb(connection: Connection) {
        connection.createStatement().execute(DROP_PRODUCT_TABLE)
        connection.createStatement().execute(DROP_RELATIONS_TABLE)
        connection.createStatement().execute(CREATE_PRODUCT_TABLE)
        connection.createStatement().execute(CREATE_RELATIONS_TABLE)
        println("Table created")
        connection.createStatement().execute("LOAD DATA LOCAL INFILE '$FILENAME' INTO TABLE $PRODUCTS_TABLE;")
        connection.createStatement().execute("LOAD DATA LOCAL INFILE '$FILENAME' INTO TABLE $RELATIONS_TABLE;")
        println("Data loaded")
    }

    companion object {
        private const val RELATIONS_TABLE = "relations"
        private const val PRODUCTS_TABLE = "product"
        private const val DROP_PRODUCT_TABLE = "DROP TABLE IF EXISTS $PRODUCTS_TABLE;"
        private const val DROP_RELATIONS_TABLE = "DROP TABLE IF EXISTS $RELATIONS_TABLE"
        private const val CREATE_PRODUCT_TABLE =
            "CREATE TABLE $PRODUCTS_TABLE (product_id1 INTEGER PRIMARY KEY, product_id2 INTEGER) ENGINE = MYISAM;"
        private const val CREATE_RELATIONS_TABLE =
            "CREATE TABLE $RELATIONS_TABLE (product_id1 INTEGER, product_id2 INTEGER, FOREIGN KEY(product_id1) REFERENCES $PRODUCTS_TABLE(product_id1)) ENGINE = MYISAM;"
        private const val FILENAME = "foo.csv"
        private const val IDS_COUNT = 2_000_000
        private const val EXCLUDE = 100_000
    }
}