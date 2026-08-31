package br.com.kelvinsouza.mealmath.security;

import br.com.kelvinsouza.mealmath.dto.TokenResponse;
import br.com.kelvinsouza.mealmath.dto.UsuarioResponse;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Gera o JWT (RF001/RF002).
 *
 * Coloco o id do usuario no sub e nao o e-mail, porque e o id que vai filtrar todas as consultas e
 * ele nao muda quando o usuario troca de e-mail. Nao coloco nada sensivel nas claims: o corpo do
 * JWT e so base64 e qualquer um com o token consegue ler.
 */
@Service
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties propriedades;

    public TokenService(JwtEncoder jwtEncoder, JwtProperties propriedades) {
        this.jwtEncoder = jwtEncoder;
        this.propriedades = propriedades;
    }

    public TokenResponse gerarPara(UsuarioAutenticado usuario) {
        Instant agora = Instant.now();

        JwtClaimsSet.Builder construtor =
                JwtClaimsSet.builder()
                        .issuer(propriedades.emissor())
                        .issuedAt(agora)
                        .expiresAt(agora.plus(propriedades.expiracao()))
                        .subject(String.valueOf(usuario.getId()))
                        .claim("email", usuario.getEmail())
                        .claim("nome", usuario.getNome());

        // Vai como texto ISO-8601 e nao como Instant. O Nimbus serializa claim de data como
        // numero de epoch, e ai o front teria que adivinhar se e segundo ou milissegundo.
        // Vem do mesmo lugar do UsuarioResponse.criadoEm, entao ao recarregar a pagina da para
        // remontar a sessao inteira so com o token, sem um GET so para o "Membro desde".
        if (usuario.getCriadoEm() != null) {
            construtor.claim("criadoEm", usuario.getCriadoEm().toString());
        }

        JwtClaimsSet claims = construtor.build();

        // O HS256 tem que ir explicito no header. Sem isso o NimbusJwtEncoder assume RS256 e nao
        // acha chave para assinar, dando "Failed to select a JWK signing key".
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        return TokenResponse.bearer(
                token,
                propriedades.expiracao().toSeconds(),
                new UsuarioResponse(
                        usuario.getId(),
                        usuario.getNome(),
                        usuario.getEmail(),
                        usuario.getCriadoEm()));
    }
}
