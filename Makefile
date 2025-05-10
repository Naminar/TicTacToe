
JAVAFX_VERSION := 17.0.15
JAVAFX_SDK_ZIP_FILENAME := openjfx-$(JAVAFX_VERSION)_linux-x64_bin-sdk.zip
JAVAFX_DOWNLOAD_URL := "https://download2.gluonhq.com/openjfx/$(JAVAFX_VERSION)/$(JAVAFX_SDK_ZIP_FILENAME)"


JAVAFX_UNZIPPED_DIR_NAME_FROM_ARCHIVE := javafx-sdk-$(JAVAFX_VERSION)


JAVAFX_LOCAL_SDK_DIR_NAME := javafx-sdk-17


JAVAFX_LOCAL_SDK_ROOT_PATH := $(CURDIR)/$(JAVAFX_LOCAL_SDK_DIR_NAME)


PATH_TO_FX_LIBS := $(JAVAFX_LOCAL_SDK_ROOT_PATH)/lib



.PHONY: all install-java download-javafx setup-javafx compile compile-server compile-client run-server run-client clean help show-env-setup


all: compile


.java_installed:
	@echo ">>> Updating package lists..."
	sudo apt update
	@echo ">>> Installing OpenJDK 17..."
	sudo apt install -y openjdk-17-jdk
	@echo ">>> Verifying Java installation:"
	@java -version
	@javac -version
	@touch $@

install-java: .java_installed
	@echo ">>> OpenJDK 17 installation check complete."



$(JAVAFX_LOCAL_SDK_ROOT_PATH):
	@echo ">>> Setting up local JavaFX SDK..."
	@if [ -f $(JAVAFX_SDK_ZIP_FILENAME) ]; then \
		echo ">>> Using existing $(JAVAFX_SDK_ZIP_FILENAME)..."; \
	else \
		echo ">>> Downloading JavaFX SDK to $(JAVAFX_SDK_ZIP_FILENAME)..."; \
		wget -c $(JAVAFX_DOWNLOAD_URL) -O $(JAVAFX_SDK_ZIP_FILENAME); \
		if [ $$? -ne 0 ]; then echo "Error: Download failed."; exit 1; fi; \
	fi
	@echo ">>> Unzipping $(JAVAFX_SDK_ZIP_FILENAME) into current directory..."
	unzip -q $(JAVAFX_SDK_ZIP_FILENAME) -d .
	@if [ ! -d "$(JAVAFX_UNZIPPED_DIR_NAME_FROM_ARCHIVE)" ]; then \
		echo "Error: Expected unzipped directory '$(JAVAFX_UNZIPPED_DIR_NAME_FROM_ARCHIVE)' not found."; \
		echo "       Please check the archive contents or JAVAFX_UNZIPPED_DIR_NAME_FROM_ARCHIVE variable."; \
		rm -f $(JAVAFX_SDK_ZIP_FILENAME); \
		exit 1; \
	fi
	@echo ">>> Renaming '$(JAVAFX_UNZIPPED_DIR_NAME_FROM_ARCHIVE)' to '$(JAVAFX_LOCAL_SDK_DIR_NAME)' in current directory..."
	mv "$(JAVAFX_UNZIPPED_DIR_NAME_FROM_ARCHIVE)" "$(JAVAFX_LOCAL_SDK_DIR_NAME)"
	@echo ">>> Local JavaFX SDK is now at $(JAVAFX_LOCAL_SDK_ROOT_PATH)"
	@echo ">>> Cleaning up $(JAVAFX_SDK_ZIP_FILENAME)..."
	rm -f $(JAVAFX_SDK_ZIP_FILENAME)


download-javafx: $(JAVAFX_LOCAL_SDK_ROOT_PATH)
	@echo ">>> Local JavaFX SDK setup check complete. SDK is at $(JAVAFX_LOCAL_SDK_ROOT_PATH)."
	@echo ">>> Library path for compilation/running is $(PATH_TO_FX_LIBS)."


setup-javafx: install-java download-javafx
	@echo ">>> Full Java and local JavaFX setup complete."



TicTacToeServer.class: TicTacToeServer.java .java_installed
	@echo ">>> Compiling TicTacToeServer.java..."
	javac TicTacToeServer.java

compile-server: TicTacToeServer.class

InfiniteTicTacToeClient.class: InfiniteTicTacToeClient.java $(JAVAFX_LOCAL_SDK_ROOT_PATH) .java_installed
	@echo ">>> Compiling InfiniteTicTacToeClient.java with JavaFX modules from $(PATH_TO_FX_LIBS)..."
	javac --module-path "$(PATH_TO_FX_LIBS)" --add-modules javafx.controls,javafx.graphics InfiniteTicTacToeClient.java

compile-client: InfiniteTicTacToeClient.class

compile: compile-server compile-client
	@echo ">>> All sources compiled."



run-server: TicTacToeServer.class
	@echo ">>> Running TicTacToeServer on port 12345..."
	@echo ">>> (Press Ctrl+C to stop the server)"
	java TicTacToeServer 12345

run-client: InfiniteTicTacToeClient.class $(JAVAFX_LOCAL_SDK_ROOT_PATH)
	@echo ">>> Running InfiniteTicTacToeClient using JavaFX from $(PATH_TO_FX_LIBS)..."
	java --module-path "$(PATH_TO_FX_LIBS)" --add-modules javafx.controls,javafx.graphics InfiniteTicTacToeClient



show-env-setup:
	@echo "---------------------------------------------------------------------"
	@echo "This Makefile manages a JavaFX SDK locally within the project at:"
	@echo "  $(JAVAFX_LOCAL_SDK_ROOT_PATH)"
	@echo "The library path used by this Makefile is:"
	@echo "  $(PATH_TO_FX_LIBS)"
	@echo ""
	@echo "If you want to compile/run manually using THIS LOCAL SDK from your shell"
	@echo "while in this project directory ($(CURDIR)), you can set:"
	@echo "  For bash/zsh: export PATH_TO_FX=\"$(PATH_TO_FX_LIBS)\""
	@echo "  For fish:     set -x PATH_TO_FX \"$(PATH_TO_FX_LIBS)\""
	@echo ""
	@echo "Alternatively, if you prefer to manage JavaFX SDK in your home directory"
	@echo "(as per your original instructions for general shell use):"
	@echo "  1. Ensure the SDK (e.g., 'javafx-sdk-17') is located at '$(HOME)/javafx-sdk-17'."
	@echo "     You might need to manually move/copy it there from '$(JAVAFX_LOCAL_SDK_ROOT_PATH)'"
	@echo "     if you used 'make download-javafx'."
	@echo "  2. Then, in your shell, set:"
	@echo "     For bash/zsh: export PATH_TO_FX=$(HOME)/javafx-sdk-17/lib"
	@echo "     For fish:     set -x PATH_TO_FX $(HOME)/javafx-sdk-17/lib"
	@echo ""
	@echo "Consider adding your preferred setup to your shell's configuration file"
	@echo "(e.g., ~/.bashrc, ~/.zshrc, or ~/.config/fish/config.fish) for persistence."
	@echo "---------------------------------------------------------------------"

clean:
	@echo ">>> Cleaning up compiled class files and local JavaFX SDK..."
	rm -f *.class
	rm -rf "$(JAVAFX_LOCAL_SDK_DIR_NAME)"
	rm -f $(JAVAFX_SDK_ZIP_FILENAME)
	rm -f .java_installed
	@echo ">>> Cleanup complete."

help:
	@echo "Makefile for TicTacToe JavaFX Application"
	@echo ""
	@echo "Usage: make [target]"
	@echo ""
	@echo "Targets:"
	@echo "  all                Builds everything (default)."
	@echo "  install-java       Installs OpenJDK 17 (if not already marked as installed)."
	@echo "  download-javafx    Downloads and sets up JavaFX SDK *locally* in this project (./javafx-sdk-17)."
	@echo "  setup-javafx       Runs both install-java and download-javafx."
	@echo "  compile-server     Compiles the TicTacToeServer."
	@echo "  compile-client     Compiles the InfiniteTicTacToeClient using the local JavaFX SDK."
	@echo "  compile            Compiles both server and client."
	@echo "  run-server         Runs the TicTacToeServer (port 12345)."
	@echo "  run-client         Runs the InfiniteTicTacToeClient using the local JavaFX SDK."
	@echo "  show-env-setup     Shows how to set PATH_TO_FX for your shell environment, referencing the local SDK."
	@echo "  clean              Removes compiled files and the locally downloaded JavaFX SDK."
	@echo "  help               Shows this help message."
	@echo ""
	@echo "Example workflow:"
	@echo "  1. make setup-javafx  (one-time setup for Java and local JavaFX SDK)"
	@echo "  2. make               (to compile)"
	@echo "  3. make run-server    (in one terminal)"
	@echo "  4. make run-client    (in another terminal)"