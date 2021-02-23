@echo off
set installPath=C:\bin
set java_home=C:\Program Files\Java\jdk-11
set javaexe=%java_home%\bin\java.exe

set cp=%installPath%\UploadFileToDP-1.0.0.jar;%java_home%\jaxb-ri\lib\jaxb-core-3.0.0.jar;%java_home%\jaxb-ri\lib\jaxb-impl-3.0.0.jar;%java_home%\jaxb-ri\lib\jakarta.xml.bind-api-3.0.0.jar;%java_home%\jaxb-ri\lib\jaxb-xjc-3.0.0.jar;%java_home%\jaxb-ri\lib\jaxb-jxc-3.0.0.jar;%java_home%\jaxb-ri\lib\jakarta.activation-2.0.0.jar;

set userId=admin
set userPwd=123456
set dp_host=dpdpod.fr.ibm:5550

set inputFile=C:\temp\file2.xml

"%javaexe%" -cp "%cp%" datapower.ibm.com.SOMALoadFiles -action load -host %dp_host% -FILE "%inputFile%" -userId %userId% -userPwd %userPwd%