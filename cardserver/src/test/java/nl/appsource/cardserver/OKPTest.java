package nl.appsource.cardserver;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import org.junit.jupiter.api.Test;

public class OKPTest {

    @Test
    void testAll() throws JOSEException {

        ECKey ecKey = new ECKeyGenerator(Curve.P_521).generate();

        // 2. Print the full key pair
        System.out.println("Generated Ed25519 Key Pair:");
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
