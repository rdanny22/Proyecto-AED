package com.sgai.modelo;

import com.sgai.util.Validacion;
import java.math.BigDecimal;

/** Nota de un estudiante, curso y período; nunca usa punto flotante binario. */
public record Calificacion(
  long id,
  long estudianteId,
  long cursoId,
  long docenteId,
  int periodo,
  BigDecimal nota
) {
  public Calificacion {
    if (id < 0) throw new IllegalArgumentException("Identidad negativa");
    Validacion.identificador(estudianteId);
    Validacion.identificador(cursoId);
    Validacion.identificador(docenteId);
    Validacion.rango(periodo, 1, 4, "Período");
    nota = Validacion.nota(nota);
  }
}
