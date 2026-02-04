#!/bin/bash

keytool -genkeypair -alias key0 -keyalg RSA -keysize 2048 -validity 365 -dname "CN=localhost, OU=MyUnit, O=MyOrganization, L=MyCity, ST=MyState, C=US" -keystore keystore.jks -storepass password -keypass password

