package com.sgai.controlador;

import com.sgai.estructuras.*;
import com.sgai.modelo.*;
import java.util.*;

/** Instantánea coherente; se publica únicamente después del commit. */
public final class EstadoAcademico {

  private final ListaEnlazadaEstudiantes estudiantes =
    new ListaEnlazadaEstudiantes();
  private final ListaEnlazadaCalificaciones calificaciones =
    new ListaEnlazadaCalificaciones();
  private final ListaEnlazadaDocentes docentes = new ListaEnlazadaDocentes();
  private final List<Curso> cursos;
  private final List<Asignacion> asignaciones;
  private final Estudiante[] indice;
  private final String[] codigos;
  private final Docente[] indiceDocentes;
  private final String[] codigosDocentes;
  private final Calificacion[] reporteOrdenado;

  public EstadoAcademico(
    List<Estudiante> alumnos,
    List<Docente> profesores,
    List<Curso> materias,
    List<Asignacion> relaciones,
    List<Calificacion> notas
  ) {
    alumnos.forEach(estudiantes::agregar);
    notas.forEach(calificaciones::agregar);
    profesores.forEach(docentes::agregar);
    cursos = List.copyOf(materias);
    asignaciones = List.copyOf(relaciones);
    indice = estudiantes.aArreglo();
    Algoritmos.ordenar(indice, Comparator.comparing(Estudiante::getCodigo));
    codigos = new String[indice.length];
    for (int i = 0; i < indice.length; i++) codigos[i] = indice[i].getCodigo();
    indiceDocentes = docentes.aArreglo();
    Algoritmos.ordenar(
      indiceDocentes,
      Comparator.comparing(Docente::getCodigo)
    );
    codigosDocentes = new String[indiceDocentes.length];
    for (int i = 0; i < indiceDocentes.length; i++) codigosDocentes[i] =
      indiceDocentes[i].getCodigo();
    reporteOrdenado = calificaciones.aArreglo();
    Algoritmos.ordenar(
      reporteOrdenado,
      Comparator.comparing(Calificacion::nota)
        .thenComparingLong(Calificacion::estudianteId)
        .thenComparingInt(Calificacion::periodo)
    );
  }

  public Estudiante[] estudiantes() {
    return estudiantes.aArreglo();
  }

  public List<Docente> docentes() {
    return List.of(docentes.aArreglo());
  }

  public List<Curso> cursos() {
    return cursos;
  }

  public List<Asignacion> asignaciones() {
    return asignaciones;
  }

  public Calificacion[] calificaciones() {
    return calificaciones.aArreglo();
  }

  /** Índice construido una sola vez por instantánea: búsqueda repetida O(log n). */
  public Estudiante buscarEstudiante(String codigo) {
    int posicion = Algoritmos.buscarBinario(
      codigos,
      codigo.toUpperCase(Locale.ROOT),
      Comparator.naturalOrder()
    );
    return posicion < 0 ? null : indice[posicion];
  }

  /** Índice docente independiente, con la misma precondición de ordenamiento. */
  public Docente buscarDocente(String codigo) {
    int posicion = Algoritmos.buscarBinario(
      codigosDocentes,
      codigo.toUpperCase(Locale.ROOT),
      Comparator.naturalOrder()
    );
    return posicion < 0 ? null : indiceDocentes[posicion];
  }

  /** El ordenamiento se realiza en el trabajador al construir la instantánea. */
  public Calificacion[] reporte() {
    return reporteOrdenado.clone();
  }
}
