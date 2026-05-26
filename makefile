APP_NAME   := NamDesktop
MAIN_CLASS := namdesktop.app.NamDesktopMain
MAIN_JAR   := $(APP_NAME).jar

SRC_DIR      := src
LIB_DIR      := lib
TEST_LIB_DIR := lib/test
BUILD_DIR    := build
CLASSES_DIR  := $(BUILD_DIR)/classes
TEST_CLASSES := $(BUILD_DIR)/test-classes
APP_DIR      := $(BUILD_DIR)/app

SOURCES      := $(shell find $(SRC_DIR) -name "*.java")
TEST_SOURCES := $(shell find test -name "*.java" 2>/dev/null)

JUNIT_JAR := $(TEST_LIB_DIR)/junit-platform-console-standalone-1.10.2.jar

.PHONY: clean classes jar app run all test e2e help

all: app

help:
	@echo "Usage: make <target>"
	@echo ""
	@echo "  all       Build the app (default)"
	@echo "  run       Build and launch the app (prod mode)"
	@echo "  run-dev   Build and launch the app (dev mode — separate workspace)"
	@echo "  e2e       Build and run the e2e regression suite"
	@echo "  test      Compile and run unit tests"
	@echo "  clean     Delete build output"
	@echo ""

clean:
	rm -rf $(BUILD_DIR)

classes:
	rm -rf $(CLASSES_DIR)
	mkdir -p $(CLASSES_DIR)
	javac \
		-cp "$(LIB_DIR)/*" \
		-d $(CLASSES_DIR) \
		$(SOURCES)
	@if [ -d $(SRC_DIR)/icons ];     then cp -r $(SRC_DIR)/icons     $(CLASSES_DIR)/; fi
	@if [ -d $(SRC_DIR)/resources ]; then cp -r $(SRC_DIR)/resources $(CLASSES_DIR)/; fi
	@if [ -f $(SRC_DIR)/demo.json ]; then cp $(SRC_DIR)/demo.json $(CLASSES_DIR)/; fi
	@if [ -f $(SRC_DIR)/e2e.json ];  then cp $(SRC_DIR)/e2e.json  $(CLASSES_DIR)/; fi

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

run-dev: app
	java \
		-cp "$(APP_DIR)/$(MAIN_JAR):$(APP_DIR)/lib/*" \
		$(MAIN_CLASS) --dev

e2e: app
	java \
		-cp "$(APP_DIR)/$(MAIN_JAR):$(APP_DIR)/lib/*" \
		$(MAIN_CLASS) --e2e

test: classes
	rm -rf $(TEST_CLASSES)
	mkdir -p $(TEST_CLASSES)
	javac \
		-cp "$(LIB_DIR)/*:$(JUNIT_JAR):$(CLASSES_DIR)" \
		-d $(TEST_CLASSES) \
		$(TEST_SOURCES)
	java -cp "$(CLASSES_DIR):$(TEST_CLASSES):$(LIB_DIR)/*:$(JUNIT_JAR)" \
		org.junit.platform.console.ConsoleLauncher execute \
		--scan-class-path "$(TEST_CLASSES)"