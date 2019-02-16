package bdproc.practica_2




import bdproc.common.Utilities.setupLogging
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql._
import org.apache.spark.sql.types._
import org.apache.spark.sql.functions._

object practica2 {
  def main(args: Array[String]): Unit = {

    // Antes de lanzar el proceso al lanzar el proceso se puede indicar un parametro LimiteAlerta
    // por defecto (y para el test) este parametro vale 5000
    val limiteAlerta: Double = 3000


    val spark = SparkSession
      .builder()
      .appName("Alerta precios")
      .master("local[*]")
      .config("spark.sql.streaming.checkpointLocation", "file:///home/kc/Documentos/res-re-stream")
      .getOrCreate()

    import spark.implicits._

    setupLogging()

    // Definir clase
    case class Hogares(localizacion: String,
                       `precio por m2`: Double)

    // Creacion de esquema
    val miEsquema = new StructType()
      .add("Localizacion", StringType, true)
      .add("Precio por m2", DoubleType, true)


    // Cargamos los parametros de salida de la Fase 1
    // Se observa el directorio y se van procesando los archivos según van apareciendo
    // Se puede simular dejando sólo unos pocos archivos de la salida del notebook
    // quitando la opcion de coalesce(1) y copiando los archivos uno a uno, o en lotes,
    // para ver como se actualiza.

    val resultadosDStream = spark.readStream
      .schema(miEsquema)
      .json("file:///home/kc/Documentos/real-estate")

    // para poder sacar un output complete, es necesario aplicar una funcion de agregado

    val listaAgrupada = resultadosDStream.groupBy($"Localizacion")
                .agg(avg("Precio por m2").as("Precio por m2"))

    // se pide que la lista esté ordenada por precios de mayor a menor
    val listaOrdenada = listaAgrupada.orderBy(desc("Precio por m2"))

    // sobre esa lista se filtran las filas que son mayores al valor de alerta
    val precioSobreLimite = listaOrdenada.filter($"Precio por m2" > limiteAlerta)

    // Para escribir el resultado se usa Kafka, que recibe el DS filtrado y ordenado de forma completa
    // Se supone que  cuando Kafka tenga un evento se mandará el correo con el mensaje que ha salido de kafka
    // Para ver el resultado en consola, se ha incluido en la linea 66 y 67 ambos comandos
    // basta con descomentar el que se desea usar y comentar el otro

    val alertaPrecios = precioSobreLimite
        .select(to_json(struct($"Localizacion", $"Precio por m2")).alias("value"))
        .writeStream
        .outputMode("complete")
        .format("console")  // Comentar o descomentar para ver la salida en consola
        //.format("kafka")          // Comentar o descomentar para ver la salida en kafka
        .option("truncate",false)   // para que el display en consola sea completo
        .option("kafka.bootstrap.servers", "localhost:9092")
        .option("topic", "alertaPrecios")
        .start()      // inicio escucha activa del directorio

    alertaPrecios.awaitTermination()



  }
}
