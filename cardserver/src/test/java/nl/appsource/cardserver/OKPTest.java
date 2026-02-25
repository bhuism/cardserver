package nl.appsource.cardserver;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import org.junit.jupiter.api.Test;

public class OKPTest {

    @Test
    void testAll() throws JOSEException {

        OctetKeyPair okp = new OctetKeyPairGenerator(Curve.Ed25519).generate();

        // 2. Print the full key pair
        System.out.println("Generated Ed25519 Key Pair:");
        System.out.println(okp.toJSONString());

        // 3. Get public and private key components
        OctetKeyPair privateKey = okp;
        OctetKeyPair publicKey = okp.toPublicJWK();

        System.out.println("\nPrivate Key (with secret 'd' parameter):");
        System.out.println(privateKey.toJSONString());

        System.out.println("\nPublic Key (without secret 'd' parameter):");
        System.out.println(publicKey.toJSONString());


    }

}
