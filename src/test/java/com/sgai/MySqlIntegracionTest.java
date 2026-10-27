package com.sgai;

import static org.junit.jupiter.api.Assertions.*;

import com.sgai.controlador.*;
import com.sgai.dao.*;
import com.sgai.modelo.*;
import com.sgai.util.ExcepcionNegocio;
import java.math.BigDecimal;
import java.sql.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Requiere una BD desechable y VACÍA con el esquema aplicado. Nunca usar datos reales. */
@EnabledIfEnvironmentVariable(named = "SGAI_PRUEBAS_MYSQL", matches = "true")
class MySqlIntegracionTest {

  private static <T> T esperar(CompletableFuture<T> tarea) throws Exception {
    return tarea.get(45, TimeUnit.SECONDS);
  }

  private static void rechazar(CompletableFuture<?> tarea) {
    ExecutionException fallo = assertThrows(ExecutionException.class, () ->
      tarea.get(45, TimeUnit.SECONDS)
    );
    assertInstanceOf(ExcepcionNegocio.class, fallo.getCause());
  }

  @Test
  void recorreFlujoCompletoConPermisosYRollback() throws Exception {
    try (
      ControladorAcademico administrador = new ControladorAcademico();
      ControladorAcademico profesor = new ControladorAcademico();
      ControladorAcademico estudiante = new ControladorAcademico()
    ) {
      assertTrue(
        esperar(administrador.requiereAdministrador()),
        "La BD de integración debe ser vacía y desechable"
      );
      esperar(
        administrador.configurar(
          "admin_prueba",
          "Clave-Admin-2026".toCharArray()
        )
      );
      esperar(
        administrador.autenticar(
          "admin_prueba",
          "Clave-Admin-2026".toCharArray()
        )
      );
      rechazar(
        administrador.configurar("otro_admin", "Clave-Admin-2026".toCharArray())
      );
      esperar(
        administrador.guardarPersona(
          new Estudiante(0, "ES01", "Ana", "Pérez", "ana@colegio.edu.ni", 7)
        )
      );
      esperar(
        administrador.guardarPersona(
          new Estudiante(0, "ES02", "Luis", "Díaz", "luis@colegio.edu.ni", 8)
        )
      );
      esperar(
        administrador.guardarPersona(
          new Docente(
            0,
            "DO01",
            "Laura",
            "López",
            "laura@colegio.edu.ni",
            "Ciencias"
          )
        )
      );
      esperar(
        administrador.guardarCurso(new Curso(0, "CU07", "Matemáticas", 7))
      );
      esperar(administrador.guardarCurso(new Curso(0, "CU08", "Historia", 8)));
      EstadoAcademico datos = administrador.getEstado();
      long ana = datos.buscarEstudiante("ES01").getId(),
        luis = datos.buscarEstudiante("ES02").getId();
      long docente = datos.docentes().get(0).getId();
      long curso = datos
        .cursos()
        .stream()
        .filter(c -> c.codigo().equals("CU07"))
        .findFirst()
        .orElseThrow()
        .id();
      long otroCurso = datos
        .cursos()
        .stream()
        .filter(c -> c.codigo().equals("CU08"))
        .findFirst()
        .orElseThrow()
        .id();
      esperar(administrador.asignar(new Asignacion(docente, curso), false));
      esperar(
        administrador.crearUsuario(
          "docente_prueba",
          "Clave-Docente-2026".toCharArray(),
          Rol.DOCENTE,
          docente
        )
      );
      esperar(
        administrador.crearUsuario(
          "alumno_prueba",
          "Clave-Alumno-2026".toCharArray(),
          Rol.ESTUDIANTE,
          ana
        )
      );
      esperar(
        profesor.autenticar(
          "docente_prueba",
          "Clave-Docente-2026".toCharArray()
        )
      );
      assertEquals(1, profesor.getEstado().estudiantes().length);
      esperar(
        profesor.guardarNota(
          new Calificacion(0, ana, curso, docente, 1, new BigDecimal("59.99"))
        )
      );
      assertEquals(1, profesor.getEstado().calificaciones().length);
      rechazar(
        profesor.guardarNota(
          new Calificacion(0, luis, curso, docente, 1, BigDecimal.TEN)
        )
      );
      rechazar(
        profesor.guardarNota(
          new Calificacion(0, luis, otroCurso, docente, 1, BigDecimal.TEN)
        )
      );
      rechazar(profesor.guardarCurso(new Curso(0, "FALLO", "Sin permiso", 7)));
      rechazar(
        profesor.guardarNota(
          new Calificacion(0, ana, curso, docente, 1, new BigDecimal("70"))
        )
      );
      assertEquals(
        1,
        profesor.getEstado().calificaciones().length,
        "El fallo no publica datos fantasma"
      );
      esperar(
        estudiante.autenticar(
          "alumno_prueba",
          "Clave-Alumno-2026".toCharArray()
        )
      );
      assertEquals(ana, estudiante.getEstado().estudiantes()[0].getId());
      assertEquals(1, estudiante.getEstado().calificaciones().length);
      rechazar(
        estudiante.eliminar(
          AcademicoDAO.Tabla.CALIFICACIONES,
          estudiante.getEstado().calificaciones()[0].id()
        )
      );
      rechazar(
        estudiante.crearUsuario(
          "intruso",
          "Clave-Intruso-2026".toCharArray(),
          Rol.ADMINISTRADOR,
          null
        )
      );
      rechazar(administrador.eliminar(AcademicoDAO.Tabla.ESTUDIANTES, ana));
      rechazar(administrador.asignar(new Asignacion(docente, curso), true));
      rechazar(
        administrador.guardarPersona(
          new Estudiante(ana, "ES01", "Ana", "Pérez", "ana@colegio.edu.ni", 8)
        )
      );
      assertThrows(ExcepcionNegocio.class, () ->
        Transacciones.ejecutar(conexion -> {
          new AcademicoDAO().guardarCurso(
            conexion,
            new Curso(0, "ROLLBACK", "Debe desaparecer", 7)
          );
          throw new ExcepcionNegocio("Provocar rollback");
        })
      );
      esperar(administrador.actualizar());
      assertFalse(
        administrador
          .getEstado()
          .cursos()
          .stream()
          .anyMatch(c -> c.codigo().equals("ROLLBACK"))
      );
      // PreparedStatement no interpreta una cadena de inyección como SQL.
      try (ControladorAcademico intruso = new ControladorAcademico()) {
        rechazar(
          intruso.autenticar("' OR '1'='1", "Clave-Admin-2026".toCharArray())
        );
        assertNull(intruso.getSesion());
      }
      esperar(administrador.eliminarUsuario("docente_prueba"));
      rechazar(profesor.actualizar()); // Revocación comprobada también en el controlador.
      rechazar(administrador.eliminarUsuario("admin_prueba"));
      rechazar(administrador.eliminarUsuario("ADMIN_PRUEBA"));
    }
  }
}
