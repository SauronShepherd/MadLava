# I01 architecture

`MadLavaAgent` creates an immutable runtime context, a minimal feature registry, a bounded nonblocking queue, a daemon fixed-delay snapshot scheduler, and a daemon UTF-8 JSONL writer. The shutdown hook emits a final snapshot and closes the writer within a bounded join. Application behavior is preserved because bootstrap catches all agent failures and no transformer is installed.
