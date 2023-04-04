#!/usr/bin/env python
import configparser
import os
from jinja2 import Template
import re
import sys

def camel_to_snake(name):
    name = re.sub("(.)([A-Z][a-z]+)", r"\1_\2", name)
    return re.sub("([a-z0-9])([A-Z])", r"\1_\2", name).lower()

def mkString(*args):
    return "".join(args)

def main():
    def is_valid_utf8(content):
        try:
            content.decode('utf-8')
            return True
        except UnicodeDecodeError:
            return False

    config = configparser.ConfigParser()
    path = os.getenv("TEMPLATE_PATH")
    destination = os.getenv("DESTINATION_PATH")
    config_path = f"{path}/.default.ini"

    print(f"Reading configs from: {config_path}")
    config.read(config_path)

    data = config["DEFAULT"]
    data = dict(data)
    normal = {k: v for k, v in data.items() if not v.startswith("$")}
    expressions = {k: v[1:] for k, v in data.items() if v.startswith("$")}

    for k, v in normal.items():
        exec(f"{k} = '{v}'")
        exec(f"data['{k}'] = {k}")
    for k, _ in normal.items():
        s = f"""    {k}_ = input('What {k}? [{data[k]}]: ')
if {k}_ != "":
    data['{k}'] = {k}_
    {k} = {k}_""".strip()
        exec(s)

    for k, v in expressions.items():
        exec(f"data['{k}'] = {v}")

    for root, dirs, files in os.walk(path):
        nroot = root[len(path) :]
        # ndir = f"{destination}/{data['project_name']}{nroot}"
        ndir = f"{destination}/{nroot}"
        template = Template(ndir)
        ndir = template.render(data)
        if not os.path.isdir(ndir):
            os.makedirs(ndir)
        for f in files:
            if f != ".default.ini":
                with open(f"{root}/{f}", mode="rb") as bfile:
                    content = bfile.read()
                    if is_valid_utf8(content):
                        template = Template(open(f"{root}/{f}").read())
                        template_file_name = Template(f)
                        f = template_file_name.render(data)
                        open(f"{ndir}/{f}", "w").write(template.render(data))
                    else:
                        open(f"{ndir}/{f}", "wb").write(content)


if __name__ == '__main__':
    main()
