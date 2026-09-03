variable "do_token" {
  description = "DigitalOcean API token"
  type        = string
  sensitive   = true
}

variable "spaces_access_key_id" {
  description = "Spaces access key (also used by Litestream on the box)"
  type        = string
  sensitive   = true
}

variable "spaces_secret_access_key" {
  description = "Spaces secret key (also used by Litestream on the box)"
  type        = string
  sensitive   = true
}

variable "domain" {
  description = "Public hostname Caddy should serve (DNS must point at the droplet)"
  type        = string
}

variable "region" {
  description = "DigitalOcean region"
  type        = string
  default     = "nyc3"
}

variable "droplet_size" {
  description = "Droplet size slug"
  type        = string
  default     = "s-1vcpu-1gb"
}

variable "volume_size_gb" {
  description = "Size of the data volume holding the SQLite file"
  type        = number
  default     = 10
}

variable "ssh_key_fingerprints" {
  description = "Fingerprints of DigitalOcean SSH keys to authorize on the droplet"
  type        = list(string)
  default     = []
}

variable "jar_path" {
  description = "Path to the built fat jar"
  type        = string
  default     = "../build/libs/pulse-all.jar"
}
