# DSpace-Scripts
Scripts for DSpace

## Scripts in this repository

(This section is incomplete)

### Copy collection configuration

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
