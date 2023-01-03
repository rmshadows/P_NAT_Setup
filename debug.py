#!/usr/bin/python3
import os

if __name__ == '__main__':
    f = os.path.join(".", "1")
    a = os.path.exists(f)
    print(a)
