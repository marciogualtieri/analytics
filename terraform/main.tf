variable "APPLICATION_VERSION" {
  type    = "string",
  default = "1.0-SNAPSHOT"
}

variable "APPLICATION_SECRET" {
  type    = "string"
}

variable "PRIVATE_KEY_FILE" {
  type    = "string"
}

provider "aws" {
  region = "eu-west-1"
}

resource "aws_instance" "analytics" {
  ami = "ami-f6c4a885"
  instance_type = "t2.micro"
  key_name = "analytics"

  tags {
    Name = "analytics"
  }

  provisioner "file" {
    source      = "../target/universal/analytics-${var.APPLICATION_VERSION}.tgz"
    destination = "analytics-${var.APPLICATION_VERSION}.tgz"
    connection = {
      type = "ssh"
      user = "ec2-user"
      private_key = "${file(var.PRIVATE_KEY_FILE)}"
      timeout = "1m"
    }
  }

  provisioner "remote-exec" {
    inline = [
      "sudo tar -xzvf analytics-${var.APPLICATION_VERSION}.tgz",
      "sudo nohup analytics-${var.APPLICATION_VERSION}/bin/analytics -Dplay.http.secret.key=${var.APPLICATION_SECRET} -Dplay.evolutions.autoApply=true &",
      "sleep 1"
    ]
    connection = {
      type = "ssh"
      user = "ec2-user"
      private_key = "${file(var.PRIVATE_KEY_FILE)}"
      timeout = "1m"
    }
  }
}
