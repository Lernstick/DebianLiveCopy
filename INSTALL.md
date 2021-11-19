# Lernstick Storage Media Management Setup

[![GitLab pipeline status](https://gitlab.fhnw.ch/ip34-21vt/ip34-21vt_lernstick/storage-media-management/badges/develop/pipeline.svg)](https://gitlab.fhnw.ch/ip34-21vt/ip34-21vt_lernstick/storage-media-management/-/commits/develop)

This is a work in progress project for a new version of the [DebianLiveCopy](https://github.com/Lernstick/DebianLiveCopy) application.

[[_TOC_]]

## Prerequisites

This application is designed to run in the [Lernstick](https://lernstick.ch/) environment.

Please setup a Lernstick environment first on a dedicated media or in a virtual machine (recommend).

- [Download Lernstick](https://releases.lernstick.ch/)
- [User manual](https://lernstick-doc.readthedocs.io/)


### Git

First, make sure that your [Git configuration](https://git-scm.com/book/en/v2/Getting-Started-First-Time-Git-Setup) works in Lernstick and that you have [added your SSH public keys to your account](https://docs.gitlab.com/ce/ssh/#add-an-ssh-key-to-your-gitlab-account).

### Additional packages

We require the following additional packages:

```sh
sudo apt update
sudo apt install --no-install-recommends -y checkstyle gradle openjfx
```

### Source code

Choose any directory (e.g. `~/NetBeansProjects`) and clone the project and its dependencies:

```sh
mkdir -p ~/NetBeansProjects
cd ~/NetBeansProjects
git clone https://github.com/Lernstick/lernstickTools
git clone https://github.com/Lernstick/jbackpack
git clone git@gitlab.fhnw.ch:ip34-21vt/ip34-21vt_lernstick/storage-media-management.git
```

_Note: All repositories must be cloned to the same parent directory._

## Build & Run

_Note: This works only on the `develop` branch but not yet on `master`._

Enter the project directory (e.g. `cd ~/NetBeansProjects/storage-media-management`) and give it a try:

```sh
cd ~/NetBeansProjects/lernstickTools
gradle jar
cd ~/NetBeansProjects/storage-media-management
ant jar
```

And to start the application:

```sh
cd ~/NetBeansProjects/storage-media-management
ant run
```

## Development

The project was initially build using Apache NetBeans. Therefore this is the easiest way to start coding on the project.

### NetBeans

It's strongly recommend to get the latest release before starting. Unfortunately they are not packaged for Debian, but you can get the latest release from [their website](https://netbeans.apache.org/download/). Follow the instructions in their installer.

To import the project use "Open Project" and...

1. ...select the `lernstickTools` folder
2. agree to resolve the project problem by setting up Gradle
3. ...select the `jbackpack` folder
4. agree to install the `nb-javac` plugin and follow the instructions in the wizard
5. ...select the `storage-media-management` folder
6. ignore the dialog about the missing `openjfx` library (this is a false positive)
7. verify that you can build and run the project in NetBeans
8. hack & profit.

### Scene Builder

We use the [JavaFX Scene Builder](https://gluonhq.com/products/scene-builder/) to build the graphical user interface.

```sh
sudo apt update
sudo apt install --no-install-recommends -y scenebuilder
```

The FXML files are located in `src/main/resources/fxml`, the corresponding code in `src/main/java/ch/fhnw/dlcopy/gui/javafx`.

### Guidelines & Testing

Our guidelines and procedure can be found in the [Construction section](https://www.cs.technik.fhnw.ch/confluence20/display/VT342105/Construction) of our project wiki. Our project management and issue tracker is [here](https://www.cs.technik.fhnw.ch/jira20/browse/VT342105).
