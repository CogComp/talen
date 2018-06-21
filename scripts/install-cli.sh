mvn package spring-boot:repackage

INSTALL_DIR=$HOME/software/talen/

mkdir -p $INSTALL_DIR

cp target/talen-0.1.0-spring-boot.jar $INSTALL_DIR

echo "java -jar $INSTALL_DIR/talen-0.1.0-spring-boot.jar -indir \$@" > $INSTALL_DIR/talen-cli

chmod +x $INSTALL_DIR/talen-cli

echo "Don't forget to add $INSTALL_DIR to your path!"










