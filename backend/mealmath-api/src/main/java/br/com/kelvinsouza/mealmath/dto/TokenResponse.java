package br.com.kelvinsouza.mealmath.dto;

/**
 * Resposta do cadastro e do login. Os dois devolvem token, porque o RF001 pede que o cadastro ja
 * autentique o usuario e leve ele direto para o dashboard.
 *
 * O token e o JWT assinado, que o front manda nas proximas requisicoes no header Authorization.
 * Os dados do usuario vao junto para o front nao precisar de um GET extra logo depois do login.
 */
public record TokenResponse(String token, String tipo, long expiraEmSegundos, UsuarioResponse usuario) {

    public static TokenResponse bearer(String token, long expiraEmSegundos, UsuarioResponse usuario) {
        return new TokenResponse(token, "Bearer", expiraEmSegundos, usuario);
    }
}
