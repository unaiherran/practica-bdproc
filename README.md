# Practica Big Data Processing

## Fase 1

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

Si se desea ver un informe en un sólo informe JSON, se usa la siguiente instrucción.
```
mediaPorLocalizacion.select($"Localizacion", redondeaPrecio($"Preciom2medio").as("Precio por m2"))
            .coalesce(1)
            .write
            .format("json")
            .save("file:///home/kc/Documentos/real-estate")
```
Pero al necesitar distintos ficheros para realizar la segunda fase, se elimina la linea de `coalesce(1)` para poder tener varios ficheros que nos facilitarán la comprobación del Streaming en la Fase 2

## Fase 2



## Fase 3
