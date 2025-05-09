# TicTacToe


```
sudo apt update
sudo apt install openjdk-17-jdk


Verify installation:
java -version
javac -version
```

JavaFX is no longer bundled with the JDK. Download the JavaFX SDK from [GluonHQ ](https://gluonhq.com/products/javafx/?spm=a2ty_o01.29997173.0.0.14b4c921ADFHXu)

```
wget "https://download2.gluonhq.com/openjfx/17.0.15/openjfx-17.0.15_linux-x64_bin-sdk.zip"

unzip openjfx-17.0.15_linux-x64_bin-sdk.zip
mv javafx-sdk-17.0.15 javafx-sdk-17
```

```
#bash
export PATH_TO_FX=~/javafx-sdk-17/lib

# To make this permanent, add it to your shell config file:
echo 'export PATH_TO_FX=~/javafx-sdk-17/lib' >> ~/.bashrc
source ~/.bashrc

#fish
set -x PATH_TO_FX ~/javafx-sdk-17/lib

# To make this permanent:
echo "set -x PATH_TO_FX ~/javafx-sdk-17/lib" >> ~/.config/fish/config.fish
```

```
javac TicTacToeServer.java

java TicTacToeServer 12345
```

```
javac --module-path $PATH_TO_FX --add-modules javafx.controls,javafx.graphics InfiniteTicTacToeClient.java
java --module-path $PATH_TO_FX --add-modules javafx.controls,javafx.graphics InfiniteTicTacToeClient
```