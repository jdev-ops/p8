pyfmt:
  black lib/
  black setup.py

shfmt:
  shfmt -i 2 -l -w bin/*

req:
  pip install -r requirements/dev.txt
