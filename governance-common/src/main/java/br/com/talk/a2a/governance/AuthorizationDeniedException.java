package br.com.talk.a2a.governance;

/**
 * Negacao de autorizacao. Carrega o codigo HTTP com que a borda deve responder.
 *
 * <p>401 = nao sabemos quem voce e. 403 = sabemos, e voce nao pode.
 */
public class AuthorizationDeniedException extends RuntimeException {

    private final int status;

    public AuthorizationDeniedException(int status, String message) {
        super(message);
        this.status = status;
    }

    public static AuthorizationDeniedException unauthenticated(String message) {
        return new AuthorizationDeniedException(401, message);
    }

    public static AuthorizationDeniedException forbidden(String message) {
        return new AuthorizationDeniedException(403, message);
    }

    public int status() {
        return status;
    }
}
