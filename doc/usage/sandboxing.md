# Sandboxing

while we want to use bubblewrap in the future to make sure apps can't do what they shouldn't, the following script is enough to make them unable to phone home (usage: `[envs] ./no-internet.sh android-translation-layer ...`)

```sh
#!/bin/sh
sudo unshare -n su ${USER} -c "env -i && source <<<`export -p` && `printf -- '"%s" ' "$@"`"
```

TODO: worms2armageddon / angry birds were only tested on 32-bit x86 due to tested versions not having 64bit binaries; test newer versions
