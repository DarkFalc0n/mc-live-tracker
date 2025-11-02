# Modrinth install location and profile are configurable; defaults use $HOME
MODRINTH_HOME ?= $(HOME)/.local/share/ModrinthApp
PROFILE ?= Test Live MC Map
MOD_FOLDER := $(MODRINTH_HOME)/profiles/$(PROFILE)/mods

.PHONY: all build copy install clean

all: install

build:
	gradle -p $(CURDIR) build --no-daemon

copy:
	mkdir -p "$(MOD_FOLDER)"
	# Copy the built jar(s) to the mods folder, preserving filenames
	cp $(CURDIR)/build/libs/mc-live-tracker-*.jar "$(MOD_FOLDER)/"

install: build copy

clean:
	gradle -p $(CURDIR) clean --no-daemon