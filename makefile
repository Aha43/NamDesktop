APP_NAME   := NamDesktop
MAIN_CLASS := namdesktop.app.NamDesktopMain
MAIN_JAR   := $(APP_NAME).jar

SRC_DIR     := src
LIB_DIR     := lib
BUILD_DIR   := build
CLASSES_DIR := $(BUILD_DIR)/classes
APP_DIR     := $(BUILD_DIR)/app

SOURCES := $(shell find $(SRC_DIR) -name "*.java")

.PHONY: clean classes jar app run all

all: app

clean:
	rm -rf $(BUILD_DIR)

classes:
	rm -rf $(CLASSES_DIR)
	mkdir -p $(CLASSES_DIR)
	javac \
		-cp "$(LIB_DIR)/*" \
		-d $(CLASSES_DIR) \
		$(SOURCES)

jar: classes
	mkdir -p $(APP_DIR)
	jar \
		--create \
		--file $(APP_DIR)/$(MAIN_JAR) \
		--main-class $(MAIN_CLASS) \
		-C $(CLASSES_DIR) . \
		-C . VERSION

app: jar
	rm -rf $(APP_DIR)/lib
	mkdir -p $(APP_DIR)/lib
	cp $(LIB_DIR)/*.jar $(APP_DIR)/lib/

run: app
	java \
		-cp "$(APP_DIR)/$(MAIN_JAR):$(APP_DIR)/lib/*" \
		$(MAIN_CLASS)