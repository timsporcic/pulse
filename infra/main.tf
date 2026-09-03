provider "digitalocean" {
  token             = var.do_token
  spaces_access_id  = var.spaces_access_key_id
  spaces_secret_key = var.spaces_secret_access_key
}

locals {
  bucket_name       = "pulse-${var.region}"
  deploy_bucket_url = "https://${local.bucket_name}.${var.region}.digitaloceanspaces.com/deploy"
  volume_name       = "pulse-data"
  # DigitalOcean exposes attached volumes under this stable device path
  volume_device = "/dev/disk/by-id/scsi-0DO_Volume_${local.volume_name}"
}

# One bucket: /deploy holds the jar + configs, Litestream replicates to /pulse.db
resource "digitalocean_spaces_bucket" "pulse" {
  name   = local.bucket_name
  region = var.region
  acl    = "private"
}

# Deploy artifacts. public-read so cloud-init can fetch them with plain curl;
# nothing secret lives here (credentials travel via user_data, not the bucket).
resource "digitalocean_spaces_bucket_object" "jar" {
  bucket       = digitalocean_spaces_bucket.pulse.name
  region       = var.region
  key          = "deploy/pulse-all.jar"
  source       = var.jar_path
  etag         = filemd5(var.jar_path)
  acl          = "public-read"
  content_type = "application/java-archive"
}

resource "digitalocean_spaces_bucket_object" "caddyfile" {
  bucket  = digitalocean_spaces_bucket.pulse.name
  region  = var.region
  key     = "deploy/Caddyfile"
  content = templatefile("${path.module}/templates/Caddyfile.tftpl", { domain = var.domain })
  acl     = "public-read"
}

resource "digitalocean_spaces_bucket_object" "litestream_config" {
  bucket = digitalocean_spaces_bucket.pulse.name
  region = var.region
  key    = "deploy/litestream.yml"
  content = templatefile("${path.module}/templates/litestream.yml.tftpl", {
    bucket = digitalocean_spaces_bucket.pulse.name
    region = var.region
  })
  acl = "public-read"
}

# The SQLite file lives on a volume that outlives the droplet
resource "digitalocean_volume" "data" {
  name                    = local.volume_name
  region                  = var.region
  size                    = var.volume_size_gb
  initial_filesystem_type = "ext4"
  description             = "Pulse SQLite data"
}

resource "digitalocean_droplet" "pulse" {
  name       = "pulse"
  region     = var.region
  size       = var.droplet_size
  image      = "ubuntu-24-04-x64"
  ssh_keys   = var.ssh_key_fingerprints
  volume_ids = [digitalocean_volume.data.id]

  user_data = templatefile("${path.module}/../ops/cloud-init.yml", {
    deploy_bucket_url            = local.deploy_bucket_url
    litestream_access_key_id     = var.spaces_access_key_id
    litestream_secret_access_key = var.spaces_secret_access_key
    volume_device                = local.volume_device
  })

  depends_on = [
    digitalocean_spaces_bucket_object.jar,
    digitalocean_spaces_bucket_object.caddyfile,
    digitalocean_spaces_bucket_object.litestream_config,
  ]
}

resource "digitalocean_firewall" "pulse" {
  name        = "pulse"
  droplet_ids = [digitalocean_droplet.pulse.id]

  inbound_rule {
    protocol         = "tcp"
    port_range       = "22"
    source_addresses = ["0.0.0.0/0", "::/0"]
  }

  inbound_rule {
    protocol         = "tcp"
    port_range       = "80"
    source_addresses = ["0.0.0.0/0", "::/0"]
  }

  inbound_rule {
    protocol         = "tcp"
    port_range       = "443"
    source_addresses = ["0.0.0.0/0", "::/0"]
  }

  outbound_rule {
    protocol              = "tcp"
    port_range            = "1-65535"
    destination_addresses = ["0.0.0.0/0", "::/0"]
  }

  outbound_rule {
    protocol              = "udp"
    port_range            = "1-65535"
    destination_addresses = ["0.0.0.0/0", "::/0"]
  }

  outbound_rule {
    protocol              = "icmp"
    destination_addresses = ["0.0.0.0/0", "::/0"]
  }
}

output "droplet_ip" {
  value       = digitalocean_droplet.pulse.ipv4_address
  description = "Point the domain's A record here"
}

output "bucket" {
  value = digitalocean_spaces_bucket.pulse.name
}
