package com.sgai.vista;

import com.sgai.controlador.ControladorAcademico;
import java.awt.*;
import java.util.Arrays;
import javax.swing.*;

/** Autenticación y alta inicial exclusivamente gráficas. */
public final class VentanaLogin extends VentanaBase {

  private static final long serialVersionUID = 1L;
  private final JTextField usuario = Formulario.texto("", 40);
  private final JPasswordField clave = Formulario.clave();

  public VentanaLogin(ControladorAcademico controlador) {
    super(controlador, "SGAI · Colegio Antonio Soldano");
    JPanel contenido = Estilo.panel();
    JPanel encabezado = new JPanel(new GridLayout(0, 1, 0, 8));
    encabezado.add(Estilo.titulo("Colegio Antonio Soldano"));
    encabezado.add(new JLabel("Sistema de Gestión Académica Integral"));
    contenido.add(encabezado, BorderLayout.NORTH);
    Formulario formulario = new Formulario();
    formulario.campo("Usuario", usuario);
    formulario.campo("Contraseña", clave);
    contenido.add(formulario, BorderLayout.CENTER);
    JPanel pie = new JPanel(new GridLayout(0, 1, 0, 8));
    JButton entrar = Estilo.boton("Iniciar sesión", () -> accion(this::entrar));
    pie.add(entrar);
    pie.add(
      Estilo.boton("Configurar primer administrador", () ->
        accion(this::configurar)
      )
    );
    pie.add(Estilo.boton("Comprobar conexión", () -> accion(this::comprobar)));
    pie.add(estado);
    contenido.add(pie, BorderLayout.SOUTH);
    setContentPane(contenido);
    getRootPane().setDefaultButton(entrar);
    pack();
    setMinimumSize(new Dimension(540, 390));
    setLocationRelativeTo(null);
  }

  public void comprobar() {
    ejecutar(controlador.requiereAdministrador(), vacio ->
      estado.setText(
        vacio
          ? "Primera ejecución: configure el administrador."
          : "Conexión disponible. Inicie sesión."
      )
    );
  }

  private void entrar() {
    char[] secreto = clave.getPassword();
    clave.setText("");
    try {
      ejecutar(
        controlador.autenticar(usuario.getText().strip(), secreto),
        sesion -> {
          VentanaPrincipal ventana = new VentanaPrincipal(controlador);
          ventana.setVisible(true);
          dispose();
        }
      );
    } finally {
      Arrays.fill(secreto, '\0');
    }
  }

  private void configurar() {
    Formulario formulario = new Formulario();
    JTextField nombre = formulario.campo(
      "Administrador",
      Formulario.texto("", 40)
    );
    JPasswordField primera = formulario.campo(
        "Contraseña (12–128)",
        Formulario.clave()
      ),
      segunda = formulario.campo("Confirmar contraseña", Formulario.clave());
    if (!formulario.aceptar(this, "Configuración inicial · solo una vez")) {
      primera.setText("");
      segunda.setText("");
      return;
    }
    char[] secreto = primera.getPassword(),
      confirmacion = segunda.getPassword();
    primera.setText("");
    segunda.setText("");
    try {
      if (
        !Arrays.equals(secreto, confirmacion)
      ) throw new com.sgai.util.ExcepcionNegocio(
        "Las contraseñas no coinciden."
      );
      ejecutar(
        controlador.configurar(nombre.getText().strip(), secreto),
        resultado ->
          JOptionPane.showMessageDialog(
            this,
            "Administrador creado. Ahora inicie sesión."
          )
      );
    } finally {
      Arrays.fill(secreto, '\0');
      Arrays.fill(confirmacion, '\0');
    }
  }
}
