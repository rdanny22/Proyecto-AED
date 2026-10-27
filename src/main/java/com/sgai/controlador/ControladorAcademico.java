package com.sgai.controlador;

import com.sgai.dao.*;
import com.sgai.modelo.*;
import com.sgai.util.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/** Único escritor por sesión. JDBC/PBKDF2 fuera del EDT; reglas independientes de la GUI. */
public final class ControladorAcademico implements AutoCloseable {

  private final ExecutorService ejecutor = Executors.newSingleThreadExecutor(
    tarea -> {
      Thread hilo = new Thread(tarea, "sgai-persistencia");
      hilo.setDaemon(true);
      return hilo;
    }
  );
  private final AcademicoDAO datos = new AcademicoDAO();
  private final UsuarioDAO usuarios = new UsuarioDAO();
  private volatile Usuario sesion;
  private volatile EstadoAcademico estado;
  private int intentos;
  private long bloqueadoHasta;

  public Usuario getSesion() {
    return sesion;
  }

  public EstadoAcademico getEstado() {
    return estado;
  }

  private <T> CompletableFuture<T> enviar(Callable<T> tarea) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        return tarea.call();
      } catch (RuntimeException error) {
        throw error;
      } catch (Exception error) {
        throw new CompletionException(error);
      }
    }, ejecutor);
  }

  public CompletableFuture<Boolean> requiereAdministrador() {
    return enviar(() -> Transacciones.ejecutar(usuarios::sinUsuarios));
  }

  /** Lock MySQL retenido hasta commit; release explícito y cierre como salvaguarda. */
  public CompletableFuture<Void> configurar(String nombre, char[] clave) {
    char[] copia = clave.clone();
    return enviar(() -> {
      try (Connection conexion = Conexion.abrir()) {
        usuarios.bloquearInicio(conexion);
        try {
          conexion.setAutoCommit(false);
          try {
            if (!usuarios.sinUsuarios(conexion)) throw new ExcepcionNegocio(
              "El sistema ya está configurado."
            );
            usuarios.crear(conexion, nombre, copia, Rol.ADMINISTRADOR, null);
            conexion.commit();
          } catch (SQLException | RuntimeException error) {
            try {
              conexion.rollback();
            } catch (SQLException fallo) {
              error.addSuppressed(fallo);
            }
            throw error;
          }
        } finally {
          usuarios.liberarInicio(conexion);
        }
      } catch (SQLException error) {
        throw new ExcepcionNegocio(
          "No se pudo crear el administrador. Revise la conexión y el esquema.",
          error
        );
      } finally {
        Arrays.fill(copia, '\0');
      }
      return null;
    });
  }

  public CompletableFuture<Usuario> autenticar(String nombre, char[] clave) {
    char[] copia = clave.clone();
    return enviar(() -> {
      try {
        if (
          System.currentTimeMillis() < bloqueadoHasta
        ) throw new ExcepcionNegocio(
          "Demasiados intentos. Espere 30 segundos."
        );
        Usuario usuario;
        try {
          usuario = Transacciones.ejecutar(conexion ->
            usuarios.autenticar(conexion, nombre, copia)
          );
        } catch (ExcepcionNegocio error) {
          if (++intentos >= 5) {
            bloqueadoHasta = System.currentTimeMillis() + 30_000;
            intentos = 0;
          }
          throw error;
        }
        EstadoAcademico nuevo = Transacciones.ejecutar(conexion ->
          cargar(conexion, usuario)
        );
        sesion = usuario;
        estado = nuevo;
        intentos = 0;
        return usuario;
      } finally {
        Arrays.fill(copia, '\0');
      }
    });
  }

  public CompletableFuture<EstadoAcademico> actualizar() {
    return enviar(() -> {
      exigirSesion();
      EstadoAcademico nuevo = Transacciones.ejecutar(c -> {
        comprobarVigencia(c);
        return cargar(c, sesion);
      });
      estado = nuevo;
      return nuevo;
    });
  }

  private EstadoAcademico cargar(Connection conexion, Usuario usuario)
    throws SQLException {
    List<Curso> cursos = datos.cursos(conexion);
    List<Asignacion> asignaciones = datos.asignaciones(conexion);
    List<Estudiante> alumnos = datos.estudiantes(conexion);
    List<Docente> profesores = datos.docentes(conexion);
    if (usuario.rol() == Rol.DOCENTE) {
      asignaciones = asignaciones
        .stream()
        .filter(a -> a.docenteId() == usuario.docenteId())
        .toList();
      Set<Long> ids = new HashSet<>();
      asignaciones.forEach(a -> ids.add(a.cursoId()));
      cursos = cursos
        .stream()
        .filter(c -> ids.contains(c.id()))
        .toList();
      Set<Integer> grados = new HashSet<>();
      cursos.forEach(c -> grados.add(c.grado()));
      alumnos = alumnos
        .stream()
        .filter(e -> grados.contains(e.getGrado()))
        .toList();
      profesores = profesores
        .stream()
        .filter(d -> d.getId() == usuario.docenteId())
        .toList();
    } else if (usuario.rol() == Rol.ESTUDIANTE) {
      alumnos = alumnos
        .stream()
        .filter(e -> e.getId() == usuario.estudianteId())
        .toList();
      int grado = alumnos.isEmpty() ? -1 : alumnos.get(0).getGrado();
      cursos = cursos
        .stream()
        .filter(c -> c.grado() == grado)
        .toList();
      profesores = List.of();
      asignaciones = List.of();
    }
    return new EstadoAcademico(
      alumnos,
      profesores,
      cursos,
      asignaciones,
      datos.calificaciones(conexion, usuario)
    );
  }

  private void exigirSesion() {
    if (sesion == null) throw new ExcepcionNegocio("Debe iniciar sesión.");
  }

  private void exigirAdministrador() {
    exigirSesion();
    if (sesion.rol() != Rol.ADMINISTRADOR) throw new ExcepcionNegocio(
      "Operación exclusiva del administrador."
    );
  }

  private CompletableFuture<EstadoAcademico> cambiar(
    Transacciones.Trabajo<Void> operacion
  ) {
    return enviar(() -> {
      exigirSesion();
      EstadoAcademico nuevo = Transacciones.ejecutar(conexion -> {
        // Serializa validación/escritura entre instancias. Bloqueo de fila retenido hasta commit.
        try (
          PreparedStatement bloqueo = conexion.prepareStatement(
            "SELECT id FROM usuarios WHERE rol='ADMINISTRADOR' ORDER BY id LIMIT 1 FOR UPDATE"
          );
          ResultSet filas = bloqueo.executeQuery()
        ) {
          if (!filas.next()) throw new ExcepcionNegocio("Falta administrador.");
        }
        comprobarVigencia(conexion);
        operacion.ejecutar(conexion);
        return cargar(conexion, sesion);
      });
      estado = nuevo;
      return nuevo;
    });
  }

  public CompletableFuture<EstadoAcademico> guardarPersona(Persona persona) {
    return cambiar(c -> {
      exigirAdministrador();
      if (persona instanceof Estudiante alumno && alumno.getId() != 0) {
        try (
          PreparedStatement consulta = c.prepareStatement(
            "SELECT COUNT(*) FROM calificaciones n JOIN cursos m ON m.id=n.curso_id WHERE n.estudiante_id=? AND m.grado<>?"
          )
        ) {
          consulta.setLong(1, alumno.getId());
          consulta.setInt(2, alumno.getGrado());
          try (ResultSet filas = consulta.executeQuery()) {
            filas.next();
            if (filas.getLong(1) > 0) throw new ExcepcionNegocio(
              "No puede cambiar el grado de un alumno con notas de otro grado."
            );
          }
        }
      }
      datos.guardarPersona(c, persona);
      return null;
    });
  }

  public CompletableFuture<EstadoAcademico> guardarCurso(Curso curso) {
    return cambiar(c -> {
      exigirAdministrador();
      if (curso.id() != 0) try (
        PreparedStatement consulta = c.prepareStatement(
          "SELECT COUNT(*) FROM calificaciones n JOIN estudiantes e ON e.id=n.estudiante_id WHERE n.curso_id=? AND e.grado<>?"
        )
      ) {
        consulta.setLong(1, curso.id());
        consulta.setInt(2, curso.grado());
        try (ResultSet filas = consulta.executeQuery()) {
          filas.next();
          if (filas.getLong(1) > 0) throw new ExcepcionNegocio(
            "El curso tiene notas de alumnos de otro grado."
          );
        }
      }
      datos.guardarCurso(c, curso);
      return null;
    });
  }

  public CompletableFuture<EstadoAcademico> asignar(
    Asignacion asignacion,
    boolean eliminar
  ) {
    return cambiar(c -> {
      exigirAdministrador();
      datos.asignar(c, asignacion, eliminar);
      return null;
    });
  }

  /** Verifica asignación y grado usando datos actuales, nunca sólo la caché. */
  private void validarNota(Connection conexion, Calificacion nota)
    throws SQLException {
    exigirSesion();
    if (sesion.rol() == Rol.ESTUDIANTE) throw new ExcepcionNegocio(
      "No tiene permiso para registrar notas."
    );
    if (
      sesion.rol() == Rol.DOCENTE && nota.docenteId() != sesion.docenteId()
    ) throw new ExcepcionNegocio(
      "Debe registrar la nota con su identidad docente."
    );
    try (
      PreparedStatement consulta = conexion.prepareStatement(
        "SELECT COUNT(*) FROM estudiantes e JOIN cursos c ON c.grado=e.grado JOIN asignacion_docente_curso a ON a.curso_id=c.id WHERE e.id=? AND c.id=? AND a.docente_id=?"
      )
    ) {
      consulta.setLong(1, nota.estudianteId());
      consulta.setLong(2, nota.cursoId());
      consulta.setLong(3, nota.docenteId());
      try (ResultSet filas = consulta.executeQuery()) {
        filas.next();
        if (filas.getLong(1) != 1) throw new ExcepcionNegocio(
          "El docente debe estar asignado y el alumno debe pertenecer al grado del curso."
        );
      }
    }
    if (nota.id() != 0) validarAccesoNota(conexion, nota.id());
  }

  private void validarAccesoNota(Connection conexion, long id)
    throws SQLException {
    if (sesion.rol() == Rol.ADMINISTRADOR) return;
    if (sesion.rol() != Rol.DOCENTE) throw new ExcepcionNegocio(
      "Operación no permitida."
    );
    try (
      PreparedStatement consulta = conexion.prepareStatement(
        "SELECT n.id FROM calificaciones n JOIN asignacion_docente_curso a ON a.curso_id=n.curso_id WHERE n.id=? AND a.docente_id=?"
      )
    ) {
      consulta.setLong(1, id);
      consulta.setLong(2, sesion.docenteId());
      try (ResultSet filas = consulta.executeQuery()) {
        if (!filas.next()) throw new ExcepcionNegocio(
          "Nota fuera de sus cursos asignados."
        );
      }
    }
  }

  public CompletableFuture<EstadoAcademico> guardarNota(Calificacion nota) {
    return cambiar(c -> {
      validarNota(c, nota);
      datos.guardarNota(c, nota);
      return null;
    });
  }

  public CompletableFuture<EstadoAcademico> eliminar(
    AcademicoDAO.Tabla tabla,
    long id
  ) {
    return cambiar(c -> {
      Validacion.identificador(id);
      if (tabla == AcademicoDAO.Tabla.CALIFICACIONES) validarAccesoNota(c, id);
      else exigirAdministrador();
      datos.eliminar(c, tabla, id);
      return null;
    });
  }

  public CompletableFuture<EstadoAcademico> crearUsuario(
    String nombre,
    char[] clave,
    Rol rol,
    Long personaId
  ) {
    char[] copia = clave.clone();
    CompletableFuture<EstadoAcademico> futuro = cambiar(c -> {
      exigirAdministrador();
      usuarios.crear(c, nombre, copia, rol, personaId);
      return null;
    });
    return futuro.whenComplete((resultado, error) -> Arrays.fill(copia, '\0'));
  }

  public CompletableFuture<EstadoAcademico> restablecerUsuario(
    String nombre,
    char[] clave
  ) {
    char[] copia = clave.clone();
    CompletableFuture<EstadoAcademico> futuro = cambiar(c -> {
      exigirAdministrador();
      try (
        PreparedStatement consulta = c.prepareStatement(
          "UPDATE usuarios SET clave_hash=? WHERE nombre=?"
        )
      ) {
        consulta.setString(1, com.sgai.seguridad.Claves.crear(copia));
        consulta.setString(2, nombre);
        if (consulta.executeUpdate() != 1) throw new ExcepcionNegocio(
          "Usuario no encontrado."
        );
      }
      return null;
    });
    return futuro.whenComplete((resultado, error) -> Arrays.fill(copia, '\0'));
  }

  public CompletableFuture<EstadoAcademico> eliminarUsuario(String nombre) {
    return cambiar(c -> {
      exigirAdministrador();
      if (nombre.equals(sesion.nombre())) throw new ExcepcionNegocio(
        "No puede eliminar su propia cuenta."
      );
      try (
        PreparedStatement consulta = c.prepareStatement(
          "DELETE FROM usuarios WHERE nombre=? AND id<>?"
        )
      ) {
        consulta.setString(1, nombre);
        // La identidad, no el nombre, protege contra colaciones sin distinción de mayúsculas.
        consulta.setLong(2, sesion.id());
        if (consulta.executeUpdate() != 1) throw new ExcepcionNegocio(
          "Usuario no encontrado o es su propia cuenta."
        );
      }
      return null;
    });
  }

  private void comprobarVigencia(Connection conexion) throws SQLException {
    try (
      PreparedStatement consulta = conexion.prepareStatement(
        "SELECT id FROM usuarios WHERE id=? AND rol=?"
      )
    ) {
      consulta.setLong(1, sesion.id());
      consulta.setString(2, sesion.rol().name());
      try (ResultSet filas = consulta.executeQuery()) {
        if (!filas.next()) throw new ExcepcionNegocio(
          "La cuenta ya no está autorizada. Cierre sesión."
        );
      }
    }
  }

  @Override
  public void close() {
    ejecutor.shutdown();
  }
}
