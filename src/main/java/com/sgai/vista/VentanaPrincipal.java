package com.sgai.vista;

import com.sgai.controlador.*;
import com.sgai.dao.AcademicoDAO.Tabla;
import com.sgai.modelo.*;
import com.sgai.util.ExcepcionNegocio;
import java.awt.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.swing.*;

/** Dashboard MVC: formularios tipados, navegación por rol y tablas de solo lectura. */
public final class VentanaPrincipal extends VentanaBase {

  private static final long serialVersionUID = 1L;
  private final boolean administrador;
  private final JTabbedPane pestanas = new JTabbedPane();
  private final JLabel resumen = new JLabel();
  private final JLabel resumenReporte = new JLabel();
  private final TablaDatos<Estudiante> estudiantes = new TablaDatos<>(
    new String[] { "Código", "Nombres", "Apellidos", "Correo", "Grado" },
    e -> new Object[] {
      e.getCodigo(),
      e.getNombres(),
      e.getApellidos(),
      e.getCorreo(),
      e.getGrado(),
    }
  );
  private final TablaDatos<Docente> docentes = new TablaDatos<>(
    new String[] { "Código", "Nombres", "Apellidos", "Correo", "Especialidad" },
    d -> new Object[] {
      d.getCodigo(),
      d.getNombres(),
      d.getApellidos(),
      d.getCorreo(),
      d.getEspecialidad(),
    }
  );
  private final TablaDatos<Curso> cursos = new TablaDatos<>(
    new String[] { "Código", "Curso", "Grado" },
    c -> new Object[] { c.codigo(), c.nombre(), c.grado() }
  );
  private final TablaDatos<Asignacion> asignaciones = new TablaDatos<>(
    new String[] { "Docente", "Curso" },
    a -> new Object[] { nombreDocente(a.docenteId()), nombreCurso(a.cursoId()) }
  );
  private final TablaDatos<Calificacion> notas = new TablaDatos<>(
    new String[] { "Estudiante", "Curso", "Docente", "Período", "Nota" },
    this::valoresNota
  );
  private final TablaDatos<Calificacion> reporte = new TablaDatos<>(
    new String[] { "Estudiante", "Curso", "Docente", "Período", "Nota" },
    this::valoresNota
  );

  public VentanaPrincipal(ControladorAcademico controlador) {
    super(controlador, "SGAI · Gestión académica");
    administrador = controlador.getSesion().rol() == Rol.ADMINISTRADOR;
    JPanel contenido = Estilo.panel();
    JPanel encabezado = new JPanel(new BorderLayout());
    JPanel marca = new JPanel(new GridLayout(0, 1, 0, 5));
    marca.add(Estilo.titulo("SGAI | Antonio Soldano"));
    Usuario usuario = controlador.getSesion();
    marca.add(new JLabel(usuario.nombre() + " · " + usuario.rol()));
    encabezado.add(marca, BorderLayout.WEST);
    JPanel acciones = new JPanel();
    acciones.add(
      Estilo.boton("Actualizar", () ->
        accion(() -> guardar(controlador.actualizar()))
      )
    );
    acciones.add(Estilo.boton("Cerrar sesión", () -> accion(this::salir)));
    encabezado.add(acciones, BorderLayout.EAST);
    contenido.add(encabezado, BorderLayout.NORTH);
    JPanel inicio = Estilo.panel();
    inicio.add(Estilo.titulo("Resumen académico"), BorderLayout.NORTH);
    inicio.add(resumen, BorderLayout.CENTER);
    inicio.add(
      new JLabel(
        "Las estadísticas reflejan únicamente los datos autorizados para su rol."
      ),
      BorderLayout.SOUTH
    );
    pestanas.addTab("Inicio", inicio);
    if (administrador || usuario.rol() == Rol.DOCENTE) pestanas.addTab(
      "Estudiantes",
      panelEstudiantes()
    );
    if (administrador) pestanas.addTab("Docentes", panelDocentes());
    pestanas.addTab("Cursos", panelCursos());
    if (administrador) pestanas.addTab("Asignaciones", panelAsignaciones());
    pestanas.addTab("Calificaciones", panelNotas());
    pestanas.addTab("Reportes", panelReportes());
    if (administrador) pestanas.addTab("Cuentas", panelCuentas());
    contenido.add(pestanas, BorderLayout.CENTER);
    contenido.add(estado, BorderLayout.SOUTH);
    setContentPane(contenido);
    setSize(1200, 760);
    setMinimumSize(new Dimension(900, 600));
    setLocationRelativeTo(null);
    refrescar();
  }

  private JButton boton(String nombre, Runnable tarea) {
    return Estilo.boton(nombre, () -> accion(tarea));
  }

  private JPanel panelTabla(JTable tabla, JPanel acciones) {
    JPanel panel = Estilo.panel();
    panel.add(acciones, BorderLayout.NORTH);
    panel.add(new JScrollPane(tabla), BorderLayout.CENTER);
    return panel;
  }

  private void guardar(CompletableFuture<EstadoAcademico> futuro) {
    ejecutar(futuro, nuevo -> refrescar());
  }

  private void refrescar() {
    EstadoAcademico datos = controlador.getEstado();
    estudiantes.mostrar(Arrays.asList(datos.estudiantes()));
    docentes.mostrar(datos.docentes());
    cursos.mostrar(datos.cursos());
    asignaciones.mostrar(datos.asignaciones());
    notas.mostrar(Arrays.asList(datos.calificaciones()));
    Calificacion[] ordenadas = datos.reporte();
    reporte.mostrar(Arrays.asList(ordenadas));
    long reprobadas = Arrays.stream(ordenadas)
      .filter(n -> n.nota().compareTo(new BigDecimal("60")) < 0)
      .count();
    BigDecimal suma = Arrays.stream(ordenadas)
      .map(Calificacion::nota)
      .reduce(BigDecimal.ZERO, BigDecimal::add);
    String promedio =
      ordenadas.length == 0
        ? "Sin notas"
        : suma
            .divide(
              BigDecimal.valueOf(ordenadas.length),
              2,
              java.math.RoundingMode.HALF_UP
            )
            .toPlainString();
    resumen.setText(
      "<html><h1>" +
        datos.estudiantes().length +
        " estudiantes</h1><h2>" +
        datos.docentes().size() +
        " docentes · " +
        datos.cursos().size() +
        " cursos</h2><h2>" +
        ordenadas.length +
        " calificaciones</h2><p>Promedio: " +
        promedio +
        " · Notas menores a 60: " +
        reprobadas +
        "</p></html>"
    );
    resumenReporte.setText(
      "Orden QuickSort: nota ascendente, alumno y período | Promedio: " +
        promedio +
        " | Reprobadas: " +
        reprobadas +
        " | Total: " +
        ordenadas.length
    );
  }

  private JPanel panelEstudiantes() {
    JPanel acciones = new JPanel(new FlowLayout(FlowLayout.LEFT));
    if (administrador) {
      acciones.add(boton("Nuevo", () -> editarPersona(null, true)));
      acciones.add(
        boton("Editar", () -> editarPersona(estudiantes.seleccionado(), true))
      );
      acciones.add(
        boton("Eliminar", () ->
          eliminar(Tabla.ESTUDIANTES, estudiantes.seleccionado().getId())
        )
      );
    }
    JTextField buscar = Formulario.texto("", 24);
    buscar.setColumns(12);
    acciones.add(new JLabel("Código exacto:"));
    acciones.add(buscar);
    acciones.add(
      boton("Buscar O(log n)", () -> {
        Estudiante encontrado = controlador
          .getEstado()
          .buscarEstudiante(buscar.getText().strip());
        estudiantes.mostrar(
          encontrado == null ? List.of() : List.of(encontrado)
        );
        estado.setText(
          encontrado == null
            ? "Estudiante no encontrado"
            : "Estudiante encontrado"
        );
      })
    );
    acciones.add(boton("Ver todos", this::refrescar));
    return panelTabla(estudiantes, acciones);
  }

  private JPanel panelDocentes() {
    JPanel acciones = new JPanel(new FlowLayout(FlowLayout.LEFT));
    acciones.add(boton("Nuevo", () -> editarPersona(null, false)));
    acciones.add(
      boton("Editar", () -> editarPersona(docentes.seleccionado(), false))
    );
    acciones.add(
      boton("Eliminar", () ->
        eliminar(Tabla.DOCENTES, docentes.seleccionado().getId())
      )
    );
    JTextField buscar = Formulario.texto("", 24);
    buscar.setColumns(12);
    acciones.add(new JLabel("Código exacto:"));
    acciones.add(buscar);
    acciones.add(
      boton("Buscar", () -> {
        Docente encontrado = controlador
          .getEstado()
          .buscarDocente(buscar.getText().strip());
        docentes.mostrar(encontrado == null ? List.of() : List.of(encontrado));
      })
    );
    acciones.add(boton("Ver todos", this::refrescar));
    return panelTabla(docentes, acciones);
  }

  private void editarPersona(Persona persona, boolean alumno) {
    Formulario formulario = new Formulario();
    JTextField codigo = formulario.campo(
      "Código",
      Formulario.texto(persona == null ? "" : persona.getCodigo(), 24)
    );
    JTextField nombres = formulario.campo(
      "Nombres",
      Formulario.texto(persona == null ? "" : persona.getNombres(), 80)
    );
    JTextField apellidos = formulario.campo(
      "Apellidos",
      Formulario.texto(persona == null ? "" : persona.getApellidos(), 80)
    );
    JTextField correo = formulario.campo(
      "Correo",
      Formulario.texto(persona == null ? "" : persona.getCorreo(), 150)
    );
    JSpinner grado = Formulario.numero(
      persona instanceof Estudiante e ? e.getGrado() : 1,
      1,
      11
    );
    JTextField especialidad = Formulario.texto(
      persona instanceof Docente d ? d.getEspecialidad() : "",
      80
    );
    if (alumno) formulario.campo("Grado (1–11)", grado);
    else formulario.campo("Especialidad", especialidad);
    if (!formulario.aceptar(this, alumno ? "Estudiante" : "Docente")) return;
    long id = persona == null ? 0 : persona.getId();
    Persona nueva = alumno
      ? new Estudiante(
          id,
          codigo.getText(),
          nombres.getText(),
          apellidos.getText(),
          correo.getText(),
          Formulario.entero(grado)
        )
      : new Docente(
          id,
          codigo.getText(),
          nombres.getText(),
          apellidos.getText(),
          correo.getText(),
          especialidad.getText()
        );
    guardar(controlador.guardarPersona(nueva));
  }

  private JPanel panelCursos() {
    JPanel acciones = new JPanel(new FlowLayout(FlowLayout.LEFT));
    if (administrador) {
      acciones.add(boton("Nuevo curso", () -> editarCurso(null)));
      acciones.add(boton("Editar", () -> editarCurso(cursos.seleccionado())));
      acciones.add(
        boton("Eliminar", () ->
          eliminar(Tabla.CURSOS, cursos.seleccionado().id())
        )
      );
    } else acciones.add(new JLabel("Cursos disponibles para su perfil"));
    return panelTabla(cursos, acciones);
  }

  private void editarCurso(Curso curso) {
    Formulario formulario = new Formulario();
    JTextField codigo = formulario.campo(
      "Código",
      Formulario.texto(curso == null ? "" : curso.codigo(), 24)
    );
    JTextField nombre = formulario.campo(
      "Nombre",
      Formulario.texto(curso == null ? "" : curso.nombre(), 80)
    );
    JSpinner grado = formulario.campo(
      "Grado",
      Formulario.numero(curso == null ? 1 : curso.grado(), 1, 11)
    );
    if (formulario.aceptar(this, "Curso")) guardar(
      controlador.guardarCurso(
        new Curso(
          curso == null ? 0 : curso.id(),
          codigo.getText(),
          nombre.getText(),
          Formulario.entero(grado)
        )
      )
    );
  }

  private JPanel panelAsignaciones() {
    JPanel acciones = new JPanel(new FlowLayout(FlowLayout.LEFT));
    acciones.add(boton("Asignar docente a curso", this::asignar));
    acciones.add(
      boton("Retirar asignación", () -> {
        Asignacion seleccion = asignaciones.seleccionado();
        if (
          confirmar(
            "¿Retirar asignación? No es posible si tiene calificaciones."
          )
        ) guardar(controlador.asignar(seleccion, true));
      })
    );
    return panelTabla(asignaciones, acciones);
  }

  private void asignar() {
    EstadoAcademico datos = controlador.getEstado();
    if (
      datos.docentes().isEmpty() || datos.cursos().isEmpty()
    ) throw new ExcepcionNegocio("Registre docentes y cursos primero.");
    Formulario formulario = new Formulario();
    JComboBox<Docente> docente = formulario.campo(
      "Docente",
      new JComboBox<>(datos.docentes().toArray(Docente[]::new))
    );
    JComboBox<Curso> curso = formulario.campo(
      "Curso",
      new JComboBox<>(datos.cursos().toArray(Curso[]::new))
    );
    if (formulario.aceptar(this, "Asignación docente–curso")) guardar(
      controlador.asignar(
        new Asignacion(
          ((Docente) docente.getSelectedItem()).getId(),
          ((Curso) curso.getSelectedItem()).id()
        ),
        false
      )
    );
  }

  private JPanel panelNotas() {
    JPanel acciones = new JPanel(new FlowLayout(FlowLayout.LEFT));
    if (controlador.getSesion().rol() != Rol.ESTUDIANTE) {
      acciones.add(boton("Registrar nota", () -> editarNota(null)));
      acciones.add(boton("Editar", () -> editarNota(notas.seleccionado())));
      acciones.add(
        boton("Eliminar", () ->
          eliminar(Tabla.CALIFICACIONES, notas.seleccionado().id())
        )
      );
    }
    acciones.add(new JLabel("Rojo = nota menor a 60 | Períodos 1 a 4"));
    return panelTabla(notas, acciones);
  }

  private <T> void seleccionar(
    JComboBox<T> campo,
    java.util.function.Predicate<T> condicion
  ) {
    for (int i = 0; i < campo.getItemCount(); i++) if (
      condicion.test(campo.getItemAt(i))
    ) {
      campo.setSelectedIndex(i);
      return;
    }
  }

  private void editarNota(Calificacion nota) {
    EstadoAcademico datos = controlador.getEstado();
    if (
      datos.estudiantes().length == 0 ||
      datos.cursos().isEmpty() ||
      datos.docentes().isEmpty()
    ) throw new ExcepcionNegocio(
      "Se requieren estudiantes, docentes y cursos asignados."
    );
    Formulario formulario = new Formulario();
    JComboBox<Estudiante> alumno = formulario.campo(
      "Estudiante",
      new JComboBox<>(datos.estudiantes())
    );
    JComboBox<Curso> curso = formulario.campo(
      "Curso",
      new JComboBox<>(datos.cursos().toArray(Curso[]::new))
    );
    JComboBox<Docente> docente = formulario.campo(
      "Docente",
      new JComboBox<>(datos.docentes().toArray(Docente[]::new))
    );
    JSpinner periodo = formulario.campo(
      "Período",
      Formulario.numero(nota == null ? 1 : nota.periodo(), 1, 4)
    );
    JTextField valor = formulario.campo(
      "Nota (0–100; decimal con punto)",
      Formulario.texto(nota == null ? "" : nota.nota().toPlainString(), 6)
    );
    if (nota != null) {
      seleccionar(alumno, e -> e.getId() == nota.estudianteId());
      seleccionar(curso, c -> c.id() == nota.cursoId());
      seleccionar(docente, d -> d.getId() == nota.docenteId());
    }
    if (!formulario.aceptar(this, "Calificación")) return;
    if (
      !valor.getText().matches("(?:[0-9]{1,3})(?:\\.[0-9]{1,2})?")
    ) throw new ExcepcionNegocio("Nota inválida. Ejemplo: 78.50");
    guardar(
      controlador.guardarNota(
        new Calificacion(
          nota == null ? 0 : nota.id(),
          ((Estudiante) alumno.getSelectedItem()).getId(),
          ((Curso) curso.getSelectedItem()).id(),
          ((Docente) docente.getSelectedItem()).getId(),
          Formulario.entero(periodo),
          new BigDecimal(valor.getText())
        )
      )
    );
  }

  private JPanel panelReportes() {
    JPanel acciones = new JPanel(new GridLayout(0, 1, 0, 5));
    acciones.add(
      new JLabel(
        "Reporte de calificaciones ordenado en memoria mediante QuickSort. Escala 0–100."
      )
    );
    acciones.add(resumenReporte);
    return panelTabla(reporte, acciones);
  }

  private JPanel panelCuentas() {
    JPanel panel = Estilo.panel();
    panel.add(Estilo.titulo("Administración de acceso"), BorderLayout.NORTH);
    JPanel centro = new JPanel(new GridLayout(0, 1, 8, 8));
    centro.add(
      new JLabel(
        "Cree una cuenta después de registrar a la persona. Cada persona admite una cuenta."
      )
    );
    centro.add(boton("Crear cuenta con rol", this::crearCuenta));
    centro.add(boton("Restablecer contraseña", this::restablecerCuenta));
    centro.add(boton("Eliminar cuenta", this::eliminarCuenta));
    centro.add(
      new JLabel(
        "Elimine primero la cuenta vinculada para poder dar de baja a una persona sin notas."
      )
    );
    panel.add(centro, BorderLayout.CENTER);
    return panel;
  }

  private void crearCuenta() {
    Formulario formulario = new Formulario();
    JTextField nombre = formulario.campo("Usuario", Formulario.texto("", 40));
    JPasswordField clave = formulario.campo(
      "Contraseña (12–128)",
      Formulario.clave()
    );
    JComboBox<Rol> rol = formulario.campo("Rol", new JComboBox<>(Rol.values()));
    JComboBox<Estudiante> estudiante = formulario.campo(
      "Estudiante (si aplica)",
      new JComboBox<>(controlador.getEstado().estudiantes())
    );
    JComboBox<Docente> docente = formulario.campo(
      "Docente (si aplica)",
      new JComboBox<>(
        controlador
          .getEstado()
          .docentes()
          .toArray(Docente[]::new)
      )
    );
    if (!formulario.aceptar(this, "Crear cuenta")) {
      clave.setText("");
      return;
    }
    char[] secreto = clave.getPassword();
    clave.setText("");
    try {
      Rol elegido = (Rol) rol.getSelectedItem();
      Long persona = null;
      if (elegido == Rol.ESTUDIANTE) {
        if (estudiante.getSelectedItem() == null) throw new ExcepcionNegocio(
          "Seleccione un estudiante."
        );
        persona = ((Estudiante) estudiante.getSelectedItem()).getId();
      }
      if (elegido == Rol.DOCENTE) {
        if (docente.getSelectedItem() == null) throw new ExcepcionNegocio(
          "Seleccione un docente."
        );
        persona = ((Docente) docente.getSelectedItem()).getId();
      }
      guardar(
        controlador.crearUsuario(
          nombre.getText().strip(),
          secreto,
          elegido,
          persona
        )
      );
    } finally {
      Arrays.fill(secreto, '\0');
    }
  }

  private void restablecerCuenta() {
    Formulario formulario = new Formulario();
    JTextField nombre = formulario.campo(
      "Usuario exacto",
      Formulario.texto("", 40)
    );
    JPasswordField clave = formulario.campo(
      "Nueva contraseña (12–128)",
      Formulario.clave()
    );
    if (!formulario.aceptar(this, "Restablecer contraseña")) {
      clave.setText("");
      return;
    }
    char[] secreto = clave.getPassword();
    clave.setText("");
    try {
      guardar(
        controlador.restablecerUsuario(nombre.getText().strip(), secreto)
      );
    } finally {
      Arrays.fill(secreto, '\0');
    }
  }

  private void eliminarCuenta() {
    String nombre = JOptionPane.showInputDialog(
      this,
      "Usuario exacto a eliminar (no puede eliminar su propia cuenta):"
    );
    if (
      nombre != null &&
      confirmar("¿Eliminar permanentemente la cuenta de acceso?")
    ) guardar(controlador.eliminarUsuario(nombre.strip()));
  }

  private void eliminar(Tabla tabla, long id) {
    if (
      confirmar(
        "¿Eliminar el registro? Las dependencias impedirán la baja si existen datos vinculados."
      )
    ) guardar(controlador.eliminar(tabla, id));
  }

  private String nombreAlumno(long id) {
    return Arrays.stream(controlador.getEstado().estudiantes())
      .filter(e -> e.getId() == id)
      .map(Estudiante::toString)
      .findFirst()
      .orElse("Estudiante #" + id);
  }

  private String nombreDocente(long id) {
    return controlador
      .getEstado()
      .docentes()
      .stream()
      .filter(d -> d.getId() == id)
      .map(Docente::toString)
      .findFirst()
      .orElse("Docente #" + id);
  }

  private String nombreCurso(long id) {
    return controlador
      .getEstado()
      .cursos()
      .stream()
      .filter(c -> c.id() == id)
      .map(Curso::toString)
      .findFirst()
      .orElse("Curso #" + id);
  }

  private Object[] valoresNota(Calificacion nota) {
    return new Object[] {
      nombreAlumno(nota.estudianteId()),
      nombreCurso(nota.cursoId()),
      nombreDocente(nota.docenteId()),
      nota.periodo(),
      nota.nota(),
    };
  }

  private void salir() {
    if (!confirmar("¿Cerrar sesión?")) return;
    controlador.close();
    ControladorAcademico nuevo = new ControladorAcademico();
    VentanaLogin login = new VentanaLogin(nuevo);
    login.setVisible(true);
    dispose();
  }
}
