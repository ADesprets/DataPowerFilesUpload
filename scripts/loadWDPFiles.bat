@echo on
set installPath=C:\bin
set java_home=C:\Program Files\Java\jdk-11
set javaexe=%java_home%\bin\java.exe

set cp=%installPath%\SetFile.jar;%java_home%\lib\rt.jar;

set userId=admin
set userPwd=123456
set dp_host=192.168.216.120:5550

set inputFile=C:\temp\file2.xml

"%javaexe%" -cp "%cp%" datapower.ibm.com.SOMALoadFiles -action load -host %dp_host% -FILE "%inputFile%" -userId %userId% -userPwd %userPwd%