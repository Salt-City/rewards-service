import java.io.BufferedWriter
import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random
import kotlin.random.nextInt

val file = File("test.csv")
val users = (0..2000).map { UUID.randomUUID().toString() }

file.bufferedWriter().use { b ->
    users.forEach { userId ->
        (0..1000).forEach { _ ->
            b.writeLine("$userId,${generateRandomTimeStamp()},${Random.nextDouble(0.0, 550.0)}")
        }
    }
}


fun generateRandomTimeStamp() = OffsetDateTime.of(
    2023,
    Random.nextInt((1..3)),
    1,
    1,
    1,
    1,
    1,
    ZoneOffset.UTC
).toString()

fun BufferedWriter.writeLine(s: String) {
    this.write(s)
    this.write("\n")
}