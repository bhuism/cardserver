package nl.appsource.cardsever.api;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import org.junit.jupiter.api.Test;

public class ECTest {

    @Test
    void testAll() throws JOSEException {

        ECKey ecKey = new ECKeyGenerator(Curve.P_521).generate();

        // 2. Print the full key pair
        System.out.println("Generated ES512 Key Pair:");
        System.out.println(ecKey.toJSONString());

        // 3. Get public and private key components
        ECKey privateKey = ecKey;
        ECKey publicKey = privateKey.toPublicJWK();

        System.out.println("\nPrivate Key (with secret 'd' parameter):");
        System.out.println(privateKey.toJSONString());

        System.out.println("\nPublic Key (without secret 'd' parameter):");
        System.out.println(publicKey.toJSONString());


    }

}
