#!/bin/zsh

#
# Copyright (C) 2024-2025 OpenAni and contributors.
#
# 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
# Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
#
# https://github.com/open-ani/ani/blob/main/LICENSE
#

# C++
brew install cmake
brew install ninja
brew install llvm

# libtorrent
brew install swig
brew install openssl

# RVM 
brew install rbenv
rbenv install 3.4.2
sudo gem install -n /usr/local/bin cocoapods
