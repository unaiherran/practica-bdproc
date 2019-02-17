# Practica Big Data Processing

## Fase 1

(`Fichero Practica BDP - Fase 1.json` cuaderno de Zeppelin que es necesario importar)

El objetivo es importar un fichero separado por comas *csv*, realizar las transformaciones necesarias en él para luego exportarlos en el directorio real-estate usando Spark SQL

Primero se realiza las importaciones de librerias necesarias, asi como la definición de la clase Hogares, con los nombres del los campos del **csv**, que servirán para importación. Tambien se define el esquema que evitará que al realizar la importación del **csv** se infieran tipos incorrectos.

A continuación se cargan los datos en un Dataset. Se muestran los datos sólo para control de que se han cargado correctamente

Se define una función que realiza un GET de una URL para realizar la consulta del valor actual del cambio USD/EUR [Documentación del API](https://fixer.io/documentation)

`{"success":true,"timestamp":1550358845,"base":"EUR","date":"2019-02-16","rates":{"USD":1.129649}}`

De una forma un poco tosca de procesamiento, se busca la subcadena USD y se cuentan 5 caracteres en adelante. Hubiese sido preferible encontrar alguna función de Scala que leyese el JSON, comprobase que el `success` es `true`, buscase la clave `USD` y convirtiese el valor en un `Double`, así como gestionar excepciones en el caso de que no hubiese comunicación con la API o el dato no pudiese ser convertido a `Double`

El factor de conversión de SqFt a m2 es fijo.

Con ambos valores, se generan sendas funciones definidas por usuario para realizar las transformaciones necesarias de USD a EUR y de SqFt a m2

Aunque el csv da los datos de precio por SqFt, prefiero no realizar una conversión de ese valor y realizar la dicvisión entre EUR / m2. Al intentar hacerlo todo en la misma consulta, el interprete no lo deja hacer, con lo que genero la tabla en dos pasos. Primero realizo las conversiones en una tabla, y luego la división en otra.

Además, después de ver los datos, se ve que hay veces que la misma localización se nombra de distintas maneras, a veces con espacios al principio del nombre y a veces sin espacios. Para normalizarlo genero una función equivalente al *TRIM* de SQL, que quita los espacios iniciales, intermedios y finales, convirtiendo '   Los    Gatos  ' en 'Los Gatos', con lo que evitamos generar valores de localización duplicados

Una vez se realizan todas estas transformaciones, sólo es necesario agrupar por localización y por la media del precio del m2 por localidad.

Antes de escribir, se realiza una última tranformación, la de cambiar los resultados de las medias de precio, a sólo dos decimales. Se podrían haber realizado antes, pero es una buena practica realizar los calculos con todos los decimales posibles y sólo realizar el redondeo cuando se muestran los datos.

Si se desea ver un informe en un sólo informe JSON, se usa la siguiente instrucción. (Genera el fichero `Datos de salida Fase 1.json`)
```
mediaPorLocalizacion.select($"Localizacion", redondeaPrecio($"Preciom2medio").as("Precio por m2"))
            .coalesce(1)
            .write
            .format("json")
            .save("file:///home/kc/Documentos/real-estate")
```
Pero al necesitar distintos ficheros para realizar la segunda fase, se elimina la linea de `coalesce(1)` para poder tener varios ficheros que nos facilitarán la comprobación del Streaming en la Fase 2 (Una multitud de ficheros `part-000...-c000.json`, comprimidos en el fichero `real-estate.zip`, y que se tienen que colocar el directorio `real estate`)

## Fase 2
(Fichero `practica2.scala` realizado en el IDE)
El objetivo de esta fase es mandar un correo si el precio medio excede un valor límite establecido. Como el correo es simulado lo que se realiza es mandar a Kafka un evento con el listado de las zonas que han excedido el precio límite, ordenado por precio.

Nada más empezar, sedefine la variable `limiteAlerta`, un `Double` que se configurará con el valor de alerta deseado. Una opción a considerar sería pasar ese valor como parametro a la hora de lanzar el jar, y pese a que lo he intentado he sido incapaz de hacerlo

Al realizar a traves de InteliJ hay que realizar la inicialización del entorno Spark (En Zeppelin se precarga)

El funcionamiento de Spark Streaming es muy similar al de Spark SQL, y los pasos son similares. Se carga en el stream un directorio, de manera que cuando cambien los archivos de ese directorio se vuelva a procesar los datos. Esto sería parte de un proceso más complejo, pero, para realizar la simulación de que funciona correctamente, se recomienda ir cargando los archivos pocoo a poco, para ver como cada carga de archivos el proceso hace lo que se espera de él.

A la hora de realizar la salida tenemos algún requisito por parte de Spark Streaming. Si se quiere hacer un dump completo, es necesario realizar funciones de agregación con lo que se vuelve a realizar la media de los valores. Sería más apropiado que, en lugar de cargar los valores medios, en el proceso real cargasemos los precios de cada una de las ventas, con su fecha, con lo que tendriamos una información más correcta. Pero esto se aleja de lo que pide el enunciado.

Se ordena la lista y se filtran los valores por encima del límite y esto se muestra en consola o en kafka (Comentando y descomentando las lineas 72 y 73 según se desee uno u otro.

Para poner en marcha Kafka hay que lanzar en local Zookeeper y el servidor de Kafka

```
bin/zookeeper-server-start.sh config/zkeeper.properties
bin/kafka-server-start.sh config/server.properties
```

y añadir un topic llamado `alertaPrecios`

`bin/kafka-topics.sh --create --zookeeper localhost:2181 --replication-factor 1 --partitions 1 --topic alertaPrecios`

Si queremos ver que tiene Kafka podemos ejecutar 
`bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic alertaPrecios --from-beginning`

En productivo tendriamos que cambiar las direcciones de salida de kafka, para que en lugar de apuntar a `localhost` apuntase a un servidor de Kafka de la instalación.

## Fase 3 (Opcional)

Intento hacer la fase 3 sin gran éxito, adaptando el [ejemplo suministrado](https://blog.scalac.io/scala-spark-ml.html) a los datos que tenemos. 

Para ello, y usando como punto de partida la Fase 1, leemos del **csv** y realizamos todas las transformaciones necesarias (cambio a Euros, cambio de sqFt a m2...)

Finalmente, eliminamos las localizaciones Bakersfield, Greenfield y Lockwood, por tener pocos datos para entrenar el modelo (sólo 1 por localizacion)

A partir de este momento es seguir el ejemplo, usando la Localización y el tamaño como datos de entrenamiento. Para ello se separa de forma aleatoria el 80% de los datos como datos de entrada y se usa el resto para comprobar el error, **cerca de 300.000 €**, lo cual es muy mal predictor, ya que el precio medio de un hogar es sobre los 400.000

Probablemente el modelo elegido no es el apropiado, o no tenemos datos suficientes para generar los datos.

A continuación intento hacer una predicción usando sólo los datos de tamaño, precio, baños y habitaciones (`f3_sin_loc.json`) y el del error es menor (280.000$ aprox **240.000€**) pero sigue sin ser válido. Espero que con los conocimientos que gane en proximos módulos, pueda mejorar estos valores.

### ToDo

- [ ] Fase 1 - excepciones en el GET
- [ ] Fase 1 - Libreria JSON para importar el valor de USD
- [ ] Fase 2 - Pasar por parametro el valor máximo
- [ ] Fase 3 - Mejorar predicción

