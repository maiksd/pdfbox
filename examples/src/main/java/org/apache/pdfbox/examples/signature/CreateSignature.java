/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.pdfbox.examples.signature;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSSignedGenerator;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 * An example for singing a PDF with bouncy castle.
 * A keystore can be created with the java keytool, for example:
 *
 * {@code keytool -genkeypair -storepass 123456 -storetype pkcs12 -alias test -validity 365
 *        -v -keyalg RSA -keystore keystore.p12 }
 *
 * @author Thomas Chojecki
 */
public class CreateSignature implements SignatureInterface
{
    private static BouncyCastleProvider provider = new BouncyCastleProvider();

    private PrivateKey privKey;
    private Certificate[] cert;
    private SignatureOptions options;

    /**
     * Initialize the signature creator with a keystore (pkcs12) and pin that
     * should be used for the signature.
     *
     * @param keystore is a pkcs12 keystore.
     * @param pin is the pin for the keystore / private key
     */
    public CreateSignature(KeyStore keystore, char[] pin)
    {
        try
        {
           // grabs the first alias from the keystore and get the private key. An
           // alternative method or constructor could be used for setting a specific
           // alias that should be used.
            Enumeration<String> aliases = keystore.aliases();
            String alias = null;
            if (aliases.hasMoreElements())
            {
                alias = aliases.nextElement();
            }
            else
            {
                throw new RuntimeException("Could not find alias");
            }
            privKey = (PrivateKey) keystore.getKey(alias, pin);
            cert = keystore.getCertificateChain(alias);
        }
        catch (KeyStoreException e)
        {
            e.printStackTrace();
        }
        catch (UnrecoverableKeyException e)
        {
            System.err.println("Could not extract private key.");
            e.printStackTrace();
        }
        catch (NoSuchAlgorithmException e)
        {
            System.err.println("Unknown algorithm.");
            e.printStackTrace();
        }
    }

    /**
     * Signs the given pdf file.
     *
     * @param document is the pdf document
     * @return the signed pdf document
     * @throws IOException
     */
    public File signPDF(File document) throws IOException
    {
        byte[] buffer = new byte[8 * 1024];
        if (document == null || !document.exists())
        {
            new RuntimeException("Document for signing does not exist");
        }

        // creating output document and prepare the IO streams.
        String name = document.getName();
        String substring = name.substring(0, name.lastIndexOf("."));

        File outputDocument = new File(document.getParent(), substring+"_signed.pdf");
        FileInputStream fis = new FileInputStream(document);
        FileOutputStream fos = new FileOutputStream(outputDocument);

        int c;
        while ((c = fis.read(buffer)) != -1)
        {
            fos.write(buffer, 0, c);
        }
        fis.close();
        fis = new FileInputStream(outputDocument);

        // load document
        PDDocument doc = PDDocument.load(document);

        // create signature dictionary
        PDSignature signature = new PDSignature();
        signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE); // default filter
        // subfilter for basic and PAdES Part 2 signatures
        signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
        signature.setName("signer name");
        signature.setLocation("signer location");
        signature.setReason("reason for signature");

        // the signing date, needed for valid signature
        signature.setSignDate(Calendar.getInstance());

        // register signature dictionary and sign interface
        if (options==null)
        {
            doc.addSignature(signature, this);
        }
        else
        {
            doc.addSignature(signature, this, options);
        }

        // write incremental (only for signing purpose)
        doc.saveIncremental(fis, fos);

        return outputDocument;
    }

    /**
     * SignatureInterface implementation.
     *
     * This method will be called from inside of the pdfbox and create the pkcs7 signature.
     * The given InputStream contains the bytes that are given by the byte range.
     *
     * This method is for internal use only. <-- TODO this method should be private
     *
     * Use your favorite cryptographic library to implement pkcs7 signature creation.
     */
    @Override
    public byte[] sign(InputStream content) throws IOException
    {
        CMSProcessableInputStream input = new CMSProcessableInputStream(content);
        CMSSignedDataGenerator gen = new CMSSignedDataGenerator();
        // CertificateChain
        List<Certificate> certList = Arrays.asList(cert);

        CertStore certStore = null;
        try
        {
            certStore = CertStore.getInstance("Collection", new CollectionCertStoreParameters(certList), provider);
            gen.addSigner(privKey, (X509Certificate) certList.get(0), CMSSignedGenerator.DIGEST_SHA256);
            gen.addCertificatesAndCRLs(certStore);
            CMSSignedData signedData = gen.generate(input, false, provider);
            return signedData.getEncoded();
        }
        catch (Exception e)
        {
            // should be handled
            System.err.println("Error while creating pkcs7 signature.");
            e.printStackTrace();
        }
        throw new RuntimeException("Problem while preparing signature");
    }

    public static void main(String[] args) throws KeyStoreException, CertificateException,
            IOException, NoSuchAlgorithmException
    {
        if (args.length != 3)
        {
            usage();
            System.exit(1);
        }
        else
        {
            File ksFile = new File(args[0]);
            KeyStore keystore = KeyStore.getInstance("PKCS12", provider);
            char[] pin = args[1].toCharArray();
            keystore.load(new FileInputStream(ksFile), pin);

            File document = new File(args[2]);

            CreateSignature signing = new CreateSignature(keystore, pin.clone());
            signing.signPDF(document);
        }

    }

    private static void usage()
    {
        System.err.println("Usage: java " + CreateSignature.class.getName()
                + " <pkcs12-keystore-file> <pin> <input-pdf>");
    }
}
