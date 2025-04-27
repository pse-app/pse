# PSE (Program for Splitting Expenses)

This repository contains all files related to the "Program for Splitting Expenses" App. The App was created for the ["Praxis der Software-Entwicklung"](https://sdq.kastel.kit.edu/wiki/Praxis_der_Software-Entwicklung) course at the Karlsruhe Institute of Technology (KIT). 

The course is made up of multiple phases, similar to the waterfall model, with the aim of building software as a team. The documentation for each of the phases can be found in /documentation

The phases are:

1. <ins>Requirements specification</ins>, see [1-Pflichtenheft.pdf](/documentation/1-Pflichtenheft.pdf)
2. <ins>Design</ins>, see [2-Entwurf.pdf](/documentation/2-Entwurf.pdf)
3. <ins>Implementation</ins>, see [3-Implementierung.pdf](/documentation/3-Implementierung.pdf)
4. <ins>Quality Assurance</ins>, see [4-QS-Bericht.pdf](/documentation/4-QS-Bericht.pdf)

## About the App

The goal of the app is to allow users to keep track of shared expenses and outstanding debt.
With the app friends can share restaurant/utility/any other bills that they want to split. 

The App is written in Kotlin with Jetpack Compose and relies on a server backend also written in Kotlin. Users can login through an OpenID identity provider.

## Project structure

* */client* App code
* */common* Code shared by app and server
* */debian* Config for building the server Debian package
* */integration* A control server for integrated app-server tests
* */run* Development config
* */server* Server code

## Installation

### Development

Simply set the properties in *run/config.properties* as needed and use the :server:run gradle task to start the server.

### Production (only on Debian machine)

After building the server .deb package and the app .apk follow the instructions in [INSTALL.txt](INSTALL.txt)

## License
This repository is licensed under the [MIT License](LICENSE).
