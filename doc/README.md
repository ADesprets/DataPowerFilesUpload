# This document explains the structure of the project
* The generate folder is where you can generate the classes that are then added in the src folder.
* the xmls folder contains the xsd for the xjc compiler, and the script *GenerateClasses.bat* used to generate the classes.
* scripts contains the script *loadWDPFiles.bat* file that is used when invoking in standalone
* jaxb-ri folder contains the various versions of the implementation of jax-b. This has changed quite a lot and is a pain to maintain.
* lib folder contains the jax-b librairies needed copied from the jaxb-ri folder with the write version.