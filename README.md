# accounting-integrations-website

A tool to export purchase orders and invoices from TicketUtils to QuickBooks

## Prerequisites

You will need [Leiningen][] 2.0.0 or above installed.

[leiningen]: https://github.com/technomancy/leiningen

## Running

To start a web server for the application, run:

`source set-env-vars.sh`
`lein ring server`

To create and run a jar

`lein uberjar`  
`java -jar target/accounting-integrations-website-0.1.0-SNAPSHOT-standalone.jar`

For the service to work, you'll need the following environment variables to be set
QUICKBOOKS_CLIENT_ID
QUICKBOOKS_CLIENT_SECRET
QUICKBOOKS_ENV
QUICKBOOKS_REDIRECT_URL
QUICKBOOKS_ENDPOINT


  
