package com.sgai.seguridad;

import com.sgai.util.ExcepcionNegocio;
import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** PBKDF2-HMAC-SHA256 con sal aleatoria individual y comparación constante. */
public final class Claves {

  private static final int ITERACIONES = 600_000;

  private Claves() {}

  public static String crear(char[] clave) {
    if (clave.length < 12 || clave.length > 128) throw new ExcepcionNegocio(
      "La contraseña requiere de 12 a 128 caracteres."
    );
    byte[] sal = new byte[16];
    new SecureRandom().nextBytes(sal);
    return (
      ITERACIONES +
      ":" +
      Base64.getEncoder().encodeToString(sal) +
      ":" +
      Base64.getEncoder().encodeToString(derivar(clave, sal, ITERACIONES))
    );
  }

  public static boolean verificar(char[] clave, String almacenado) {
    if (clave.length > 128) return false;
    try {
      String[] partes = almacenado.split(":");
      int rondas = Integer.parseInt(partes[0]);
      if (
        partes.length != 3 || rondas < 100_000 || rondas > 2_000_000
      ) return false;
      return MessageDigest.isEqual(
        Base64.getDecoder().decode(partes[2]),
        derivar(clave, Base64.getDecoder().decode(partes[1]), rondas)
      );
    } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException error) {
      return false;
    }
  }

  private static byte[] derivar(char[] clave, byte[] sal, int rondas) {
    PBEKeySpec especificacion = new PBEKeySpec(clave, sal, rondas, 256);
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        .generateSecret(especificacion)
        .getEncoded();
    } catch (GeneralSecurityException error) {
      throw new IllegalStateException(
        "Proveedor criptográfico no disponible",
        error
      );
    } finally {
      especificacion.clearPassword();
    }
  }
}
