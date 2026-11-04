import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Maintainer-only host tool. No private key is exported or written; no password in arguments. */
class SignContent {
    public static void main(String[] args) throws Exception {
        char[] password = System.getenv("UFUQ_SIGNING_PASSWORD").toCharArray();
        try {
            KeyStore store=KeyStore.getInstance("PKCS12");
            try(var stream=Files.newInputStream(Path.of(args[0]))){store.load(stream,password);}
            var certificate=store.getCertificate(args[1]);
            byte[] encoded=certificate.getEncoded();
            String fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded));
            if(!fingerprint.equals("de13674be215040bafb029fe637a74e9e981accfa5a763bba62ad99dbcda5169"))throw new SecurityException("Wrong existing production identity");
            byte[] payload=Files.readAllBytes(Path.of(args[2]));
            if(payload.length>262144)throw new IllegalArgumentException("Content too large");
            Signature signer=Signature.getInstance("SHA256withRSA");
            signer.initSign((PrivateKey)store.getKey(args[1],password));signer.update(payload);
            String envelope="{\"formatVersion\":1,\"payload\":\""+Base64.getEncoder().encodeToString(payload)+"\",\"signature\":\""+Base64.getEncoder().encodeToString(signer.sign())+"\"}\n";
            Files.writeString(Path.of(args[3]),envelope);
            Files.write(Path.of(args[4]),encoded); // Public certificate only, safe to bundle.
        } finally {Arrays.fill(password,'\0');}
    }
}
