#!/bin/bash

mkdir -p /usr/local/bin
mkdir darwin-temp
pushd darwin-temp

curl -O http://ftp.gnu.org/gnu/m4/m4-1.4.16.tar.bz2
tar -xjvf m4-1.4.16.tar.bz2
cd m4-1.4.16
./configure --prefix=/usr/local
make
sudo make install
cd ..
curl -O http://ftp.gnu.org/gnu/autoconf/autoconf-2.68.tar.gz
tar -xzvf autoconf-2.68.tar.gz
cd autoconf-2.68
./configure --prefix=/usr/local # ironic, isn't it?
make
sudo make install
cd ..
# here you might want to restart your terminal session, to ensure the new autoconf is picked up and used in the rest of the script
curl -O http://ftp.gnu.org/gnu/automake/automake-1.11.1.tar.gz
tar xzvf automake-1.11.1.tar.gz
cd automake-1.11.1
./configure --prefix=/usr/local
make
sudo make install
cd ..
curl -O ftp://ftp.gnu.org/gnu/libtool/libtool-2.4.2.tar.gz
tar xzvf libtool-2.4.2.tar.gz
cd libtool-2.4.2
./configure --prefix=/usr/local
make
sudo make install

popd
rm -rf darwin-temp

exit
