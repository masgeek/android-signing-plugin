openssl req -newkey rsa:2048 -keyout SignApksBuilderTest-key.pem -x509 -days 9999 -out SignApksBuilderTest.pem -subj '/CN=SignApksBuilderTest/OU=android-signing/O=org.jenkins-ci.plugins/L=Jenkins/C=IO'
echo "..."
echo "verify certificate info..."
echo "..."
openssl x509 -text -noout -in SignApksBuilderTest.pem
echo "..."
echo "create p12 key store"
echo "..."
openssl pkcs12 -inkey SignApksBuilderTest-key.pem -in SignApksBuilderTest.pem -name SignApksBuilderTest -export -out SignApksBuilderTest.p12
echo "..."
echo "verify p12 keystore..."
echo "..."
openssl pkcs12 -in SignApksBuilderTest.p12 -noout -info
