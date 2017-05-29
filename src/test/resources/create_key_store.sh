#!/usr/bin/env bash
name=${1}
[ -z ${name} ] && read -p "enter base file name for your key which will also be the entry name: " name
echo "generating new self-signed certificate - openssl will ask you for a"
read -p "private key encryption password (enter to continue)"
echo ""
openssl req -newkey rsa:2048 -keyout ${name}-key.pem -x509 -days 9999 -out ${name}-public.pem #-subj '/CN=SignApksBuilderTest/OU=android-signing/O=org.jenkins-ci.plugins/L=Jenkins/C=IO'
echo ""
echo "verifying generated certificate - openssl will ask you for the password"
read -p "you just gave it to encrypt your new private key (enter to continue)"
echo ""
openssl x509 -text -noout -in ${name}-public.pem
echo ""
read -p "create p12 key store - you must provide a password to encrypt your key store (enter to continue)"
echo ""
cat ${name}-key.pem ${name}-public.pem > ${name}-pair.pem
openssl pkcs12 -export -out ${name}.p12 -in ${name}-pair.pem -name ${name}
echo ""
echo "verify p12 keystore - you will need to provide the password you just used to"
echo "encrypt your key store, as well as a one-time-use password that openssl uses"
read -p "to encrypt the output of your private key to the console (enter to continue)"
echo ""
openssl pkcs12 -in ${name}.p12 -info
