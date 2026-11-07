// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.ai;

/** Fixed messages only: never echo a key, remote response body or exception text. */
public final class AiConnectionStatus {
    private AiConnectionStatus() {}
    public static boolean validKey(String value){return value!=null&&!value.isEmpty()&&value.length()<=256&&value.matches("[A-Za-z0-9_-]+");}
    public static String explain(String error){
        if(error==null)return "No se pudo comprobar la conexión. La escritura local sigue disponible.";
        switch(error){
            case "RATE_LIMITED":return "Google limitó la cuota o la frecuencia de solicitudes. Revisa la cuota del proyecto y prueba más tarde.";
            case "INVALID_REQUEST":case "BAD_REQUEST":return "Revisa el formato de la clave y el modelo en Avanzado. Pega solo la clave, sin comillas ni saltos de línea.";
            case "NO_KEY":return "Guarda una clave de Gemini para comprobar la conexión.";
            case "UNAUTHORIZED":return "Google no aceptó la clave. Comprueba que siga activa.";
            case "FORBIDDEN":return "La clave no tiene permisos para este servicio. Revisa su proyecto y restricciones.";
            case "MODEL_UNAVAILABLE":return "El modelo no está disponible. Usa la selección automática en Avanzado.";
            case "OFFLINE":case "TIMEOUT":return "No se pudo establecer conexión. Comprueba la red y vuelve a intentarlo.";
            default:return "No se pudo comprobar la conexión. La escritura local sigue disponible.";
        }
    }
}
