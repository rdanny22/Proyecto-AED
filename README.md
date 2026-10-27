# SGAI · Colegio Antonio Soldano

Aplicación de escritorio **Java 17 / Swing / MySQL 8**, con separación MVC/DAO, listas enlazadas propias y algoritmos explícitos. La interacción del personal y del estudiante es exclusivamente gráfica; no hay menú de consola, credenciales incorporadas ni modo de demostración que simule persistencia.

## Requisitos y ejecución

- JDK 17 o posterior y Maven 3.9+ para compilar.
- MySQL **8.0.16+** (los `CHECK` deben ser efectivos), recomendado 8.4.
- Escritorio gráfico en Windows, Linux o macOS. Swing no es una aplicación web y no se visualiza en una vista previa HTTP.

### 1. Preparar la base de datos

Con una cuenta de migraciones, ejecute **una sola vez** [`database/esquema.sql`](database/esquema.sql) en MySQL Workbench u otro cliente SQL. El script crea `sgai-soldano-db` y las seis tablas. No vuelve a crear tablas existentes ni migra esquemas previos.

Cree una cuenta MySQL **específica** para la aplicación con permisos `SELECT`, `INSERT`, `UPDATE`, `DELETE` solamente sobre `sgai-soldano-db` y acceso limitado al origen de la aplicación. No utilice `root`. Las migraciones y las copias de seguridad son responsabilidad de otra cuenta. La aplicación no necesita permisos DDL ni `GRANT`.

### 2. Configurar el entorno

Variables obligatorias:

| Variable | Propósito |
|---|---|
| `SGAI_DB_USUARIO` | Cuenta MySQL de la aplicación |
| `SGAI_DB_CLAVE` | Contraseña de esa cuenta; nunca una contraseña de un usuario escolar |
| `SGAI_DB_URL` | Opcional: URL JDBC completa |

URL predeterminada:

```text
jdbc:mysql://localhost:3306/sgai-soldano-db?sslMode=VERIFY_IDENTITY&connectTimeout=5000&socketTimeout=15000&connectionTimeZone=UTC
```

Configure certificados de confianza en la JVM para verificar la identidad del servidor. **Solo para desarrollo en su propia máquina**, se puede especificar una URL con `sslMode=DISABLED`; no desactive TLS en redes de producción. `localhost` aquí pertenece a la aplicación Java instalada, no a un navegador.

Configure las variables mediante el sistema operativo o el entorno de ejecución de su IDE. El archivo `.env` **no se carga automáticamente**. Nunca incluya secretos en el repositorio o en capturas de pantalla.

### 3. Compilar y abrir

```sh
mvn clean verify
java -jar target/sgai-soldano-1.0.0.jar
```

El JAR incluye MySQL Connector/J. En Windows también puede iniciarse con `javaw -jar ...` desde un acceso directo con el entorno configurado, sin ventana de consola. En el IDE, ejecute `com.sgai.Aplicacion`.

### 4. Primer acceso y flujo de trabajo

1. La ventana de acceso comprueba la conexión. Si falla, corrija el entorno/esquema y pulse **Comprobar conexión**.
2. Pulse **Configurar primer administrador**. Elija usuario y contraseña propia (12–128 caracteres), y confírmela. No hay contraseña predeterminada. MySQL serializa esta operación con `GET_LOCK`; no se permite si ya existe una cuenta.
3. Inicie sesión. Registre estudiantes, docentes y cursos desde sus pestañas.
4. Asigne docentes a cursos. Los grados válidos son del 1 al 11.
5. En **Cuentas**, cree los accesos Docente/Estudiante asociados a una persona. También puede restablecer contraseñas o eliminar cuentas; no puede eliminar su propia cuenta.
6. Registre notas usando estudiantes del grado del curso y un docente asignado. Hay cuatro períodos, escala 0–100 y dos decimales como máximo. Una sola nota por estudiante/curso/período.
7. En **Reportes**, consulte las notas ordenadas mediante QuickSort. Las notas inferiores a 60 aparecen en rojo y negrita, incluso seleccionadas.
8. Use **Actualizar** para incorporar cambios hechos desde otra instancia; **Cerrar sesión** descarta la sesión y su caché.

La baja física no borra cascadas académicas: se rechaza si existen relaciones. Para dar de baja una persona sin historial, retire primero su cuenta y sus asignaciones. Una asignación con notas no puede retirarse. No cambie grados como mecanismo de promoción si ya existen notas asociadas incompatibles.

## Permisos

| Operación | Administrador | Docente | Estudiante |
|---|---|---|---|
| CRUD estudiantes, docentes, cursos | Sí | No | No |
| Gestionar asignaciones y cuentas | Sí | No | No |
| Consultar estudiantes | Todos | Grados de sus cursos | Solo datos propios en el resumen |
| Consultar cursos | Todos | Asignados | De su grado |
| Crear/editar/eliminar notas | Todas | Solo cursos asignados, con su identidad docente | No |
| Ver notas y reportes | Todos | Cursos asignados | Solo sus notas |

Los permisos se verifican en el **controlador**, no únicamente ocultando botones. Las notas se filtran también mediante SQL parametrizado. La cuenta vigente se comprueba en cada actualización/escritura: eliminar una cuenta impide operaciones posteriores de su sesión. Una caché ya mostrada no puede retirarse a distancia. Restablecer una contraseña afecta al siguiente inicio de sesión, no expulsa sesiones existentes.

## Arquitectura y fundamentos

```text
com.sgai.Aplicacion
├── modelo       Persona abstracta → Estudiante / Docente; Usuario, Curso, Calificacion, Rol, Asignacion
├── estructuras  Nodos y listas propias; QuickSort y búsqueda binaria genérica
├── dao          Conexion, Transacciones, UsuarioDAO, AcademicoDAO
├── controlador  ControladorAcademico, EstadoAcademico
├── vista        Login, dashboard, formularios, tablas y renderizador
├── seguridad    PBKDF2-HMAC-SHA256
└── util         Validación y excepción de negocio
```

- **POO (Deitel / Joyanes):** identidad encapsulada, objetos de dominio inmutables, herencia mediante `Persona`, sobrescritura polimórfica de `getDescripcion`, interfaces funcionales y composición en el controlador.
- **Listas propias:** estudiantes, docentes y notas se almacenan en estructuras simples con cabeza/cola. Inserción O(1), búsqueda por ID y eliminación O(n), copia O(n); memoria O(n). No exponen los enlaces mutables.
- **QuickSort propio:** construye índices por código y el reporte (nota ascendente, ID de alumno y período). O(n log n) promedio, O(n²) peor caso; recursión sobre el tramo menor para limitar la pila a O(log n). No se afirma que sea estable.
- **Búsqueda binaria propia:** O(log n) en índices de estudiantes y docentes construidos una vez por instantánea; requisito explícito de ordenamiento por el mismo comparador. Construir cada índice cuesta O(n log n) promedio y O(n) memoria.
- **MVC / DAO:** las vistas no ejecutan SQL; los DAO no conocen Swing; el controlador impone las reglas y publica instantáneas tras confirmar la transacción.
- **Asincronía:** un `ExecutorService` por sesión ejecuta JDBC, hashing, índices y ordenamiento. `CompletableFuture` devuelve resultados al EDT con `SwingUtilities.invokeLater`. No se muestran cambios optimistas que después pudieran fallar en la BD.
- **Transacciones:** commit/rollback y `try-with-resources`. Las escrituras de la aplicación se serializan entre instancias bloqueando una fila administrativa, para validar asignación y grado sin carreras entre escrituras cooperantes. Una instantánea se construye en la misma unidad de trabajo y se publica solo después del commit.
- **Errores:** validaciones regex, selectores numéricos, notas `BigDecimal`, errores de integridad traducidos y mensajes gráficos sin SQL ni secretos. Una transacción fallida conserva la última instantánea confirmada.
- **Credenciales:** PBKDF2-HMAC-SHA256, 600 000 iteraciones, sal aleatoria de 16 bytes, salida de 256 bits, comparación constante, borrado de arreglos temporales. Mensaje de credenciales genérico; cinco fallos ocasionan una espera de 30 segundos en esa instancia.

### Diseño relacional en 1FN

Todos los campos contienen valores atómicos; no hay listas serializadas ni grupos repetidos. Las PK identifican cada fila, los códigos y usuarios son únicos, y las FK tienen `ON DELETE RESTRICT`.

- `estudiantes`, `docentes`: identidad y atributos atómicos especializados.
- `usuarios`: rol y asociación opcional, con `CHECK` que impone exactamente la persona correspondiente para cada rol.
- `cursos`: código, nombre y grado.
- `asignacion_docente_curso`: PK compuesta `(docente_id, curso_id)`, relación muchos a muchos.
- `calificaciones`: PK propia; unicidad `(estudiante_id, curso_id, periodo)`; FK compuesta hacia la asignación. Esto impide notas con docentes no asignados, incluso ante SQL directo. La compatibilidad de grado entre alumno y curso se valida transaccionalmente en el controlador.

## Verificación

`mvn verify` ejecuta siete grupos JUnit: dominio, listas, algoritmos, instantáneas, claves, autorización sin sesión y componentes Swing en modo headless. Los algoritmos se comparan contra un oráculo de biblioteca en cientos de arreglos y miles de búsquedas; se incluyen vacíos, duplicados y un arreglo de 100 000 elementos iguales.

La prueba `MySqlIntegracionTest` está **desactivada por defecto**. Para habilitarla:

1. Cree una BD desechable y vacía con el esquema, en una instancia separada.
2. Configure las variables JDBC apuntando a ella y `SGAI_PRUEBAS_MYSQL=true`.
3. Ejecute `mvn verify`. La prueba deja datos de prueba; recree la BD antes de volver a ejecutarla. **Nunca apunte esta prueba a datos reales**.

Comprueba aprovisionamiento, login, CRUD base, roles, notas duplicadas, grados, restricciones FK, rollback sin datos fantasma, revocación e intento de inyección. El flujo de GitHub Actions provisiona MySQL 8.4 y ejecuta esta misma prueba. Las credenciales visibles en ese flujo son exclusivamente del contenedor efímero de CI.

### Estado de comprobación en este entorno

- Las 32 clases de aplicación y el núcleo de pruebas se compilaron con Eclipse Compiler for Java, destino Java 17.
- El núcleo `Verificaciones` se ejecutó correctamente en un runtime Java 17, incluyendo Swing headless; no produce salida si todas las verificaciones pasan.
- No se ejecutaron aquí Maven, MySQL real ni ventanas gráficas: el entorno no los proporciona. Las pruebas de integración están implementadas y configuradas en CI, **no se presentan como ya superadas**.

## Alcance y despliegue responsable

El documento final UNI no está adjunto. Esta implementación toma únicamente las especificaciones del mensaje como contrato; no se afirma certificación ni correspondencia textual con las obras citadas.

Decisiones explícitas:

- Un conjunto académico por instalación, sin años lectivos, matrícula histórica, secciones, asistencia ni promociones automáticas. Los estudiantes acceden a cursos de su grado. Para varios años se necesita una migración de modelo, no reutilizar notas de otro ciclo.
- Promedio mostrado = media aritmética de las calificaciones autorizadas, sin ponderaciones ni fórmula de promedio final no especificada.
- Reportes en pantalla, sin exportación PDF/Excel no solicitada.
- Instantáneas completas en memoria: apropiado para un colegio; requiere paginación y estrategias incrementales para grandes volúmenes. Edición concurrente del mismo registro: prevalece la última escritura confirmada; no hay historial ni versionado optimista.
- Aplicación de escritorio con JDBC directo: requiere equipos y conexión de BD **de confianza**. Quien controla una máquina cliente con credenciales MySQL puede eludir el control de roles de Java. Si los estudiantes usan equipos no confiables, **no distribuya esta configuración**: interponga un servicio backend con autenticación y autorización por solicitud y retire los secretos de BD del cliente.
- El bloqueo de intentos es local al proceso; no sustituye controles centralizados, MFA ni auditoría. Proteja el primer aprovisionamiento en una estación administrada.
- Antes de producción: ejecutar CI e integración contra su versión MySQL, hacer pruebas gráficas/manuales con los tres roles, revisar requisitos UNI, políticas de privacidad, permisos y certificados; configurar respaldos y probar su recuperación. El proyecto no incluye auditoría persistente, recuperación por correo ni actualización automática de esquema.

Estos límites evitan presentar un proyecto académico verificable como un despliegue de producción ya auditado.
