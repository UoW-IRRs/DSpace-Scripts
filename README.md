# DSpace-Scripts

Scripts in this repository generally need to be invoked via

````
sudo -u tomcat [dspace]/bin/dspace dsrun [classname]
````

Substitute actual user that DSpace/tomcat runs under for "tomcat", actual DSpace installation directory for "[dspace]" and actual fully qualified class name for "[classname]".

## Scripts in this repository

(This section is incomplete)

### Add bitstream to item from command line

Add a bitstream to an item via the command line. Optionally, specify the description and/or the name of the target bundle.

Class name for dsrun: nz.ac.lconz.irr.scripts.AddBitstreamFromCLI


````
usage: AddBitstreamFromCLI options
 -b,--bundle <arg>        Name of the bundle that this file should be
                          added to (optional). If not given, ORIGINAL is
                          used.
 -d,--description <arg>   The file description (optional)
 -f,--file <arg>          File to add
 -h,--help                Print help for this command and exit without
                          taking any action.
 -i,--identifier <arg>    Handle or ID of item to add bitstream to
````

### Copy collection configuration

Copy aspects of collection configuration from one collection to one or more others.

Class name for dsrun: nz.ac.lconz.irr.scripts.CopyCollectionConfiguration


````
usage: CopyCollectionConfiguration options
 -c,--components <arg>   The component to copy. Can be specified multiple
                         times to copy multiple components, but at least
                         one is required. Available components are almstw
                         for a=administrators, l=logo, m=metadata,
                         s=submitters, t=template, w=workflow.
 -f,--from <arg>         The handle or ID of the collection from which to
                         copy the configuration. Required.
 -h,--help               Print help for this command and exit without
                         taking any action.
 -t,--to <arg>           Handle or ID of the collection to which to copy
                         the configuration. Can be specified multiple
                         times to process multiple collections. At least
                         one is required.
````


